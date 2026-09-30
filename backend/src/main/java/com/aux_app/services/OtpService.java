package com.aux_app.services;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.aux_app.entity.OtpCodeEntity;
import com.aux_app.error.AuxException;
import com.aux_app.repository.OtpCodeRepository;

/** Issues and checks the 6-digit codes that gate password and email changes. */
@Service
public class OtpService {

    static final Duration TTL = Duration.ofMinutes(10);
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    static final int MAX_ATTEMPTS = 5;

    private final OtpCodeRepository codes;
    private final EmailService email;
    private final SecureRandom random = new SecureRandom();
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    public OtpService(OtpCodeRepository codes, EmailService email) {
        this.codes = codes;
        this.email = email;
    }

    /** Emails a fresh code to `to`, replacing any earlier one. pendingEmail: null for a password change, else the new address. */
    public void send(String userId, String to, String pendingEmail) {
        issue(userId, pendingEmail, code -> email.sendOtpCode(to, code));
    }

    /**
     * For an email change to an address that already has an account. Stores a code like send() but emails `to` a
     * notice instead, so the response and cooldown look the same and the caller can't tell the address is registered.
     */
    public void sendTakenNotice(String userId, String to) {
        issue(userId, to, code -> email.sendEmailTakenNotice(to));
    }

    private void issue(String userId, String pendingEmail, Consumer<String> deliver) {
        codes.findById(userId).ifPresent(old -> {
            if (Instant.now().isBefore(old.getSentAt().plus(RESEND_COOLDOWN))) {
                throw new AuxException(HttpStatus.TOO_MANY_REQUESTS, "OTP_COOLDOWN", "Wait a minute before requesting another code");
            }
        });
        String code = "%06d".formatted(random.nextInt(1_000_000));
        // Send first: if Resend fails nothing is stored, so the cooldown doesn't block a retry
        deliver.accept(code);
        codes.save(new OtpCodeEntity(userId, bcrypt.encode(code), TTL, pendingEmail));
    }

    /** Checks the code and burns it on success. Returns the stored pendingEmail (null for a password-change code). */
    public String consume(String userId, String code) {
        OtpCodeEntity otp = codes.findById(userId).orElse(null);
        if (otp == null || otp.isExpired() || otp.getAttempts() >= MAX_ATTEMPTS) {
            if (otp != null) codes.delete(otp);
            throw invalid();
        }
        if (!bcrypt.matches(code, otp.getCodeHash())) {
            otp.recordFailedAttempt();
            codes.save(otp);
            throw invalid();
        }
        codes.delete(otp);
        return otp.getPendingEmail();
    }

    public static AuxException invalid() {
        return new AuxException(HttpStatus.BAD_REQUEST, "INVALID_OTP", "Code is wrong or expired; request a new one", "code");
    }
}

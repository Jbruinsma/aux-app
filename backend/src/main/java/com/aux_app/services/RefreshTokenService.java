package com.aux_app.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aux_app.entity.RefreshTokenEntity;
import com.aux_app.error.AuxException;
import com.aux_app.repository.RefreshTokenRepository;

/**
 * Long-lived, single-use tokens that keep a session going past the 1-hour access JWT. A family is the chain of tokens
 * from one login; its id rides in the JWT as 'sid' so a session can be told apart from the user's others.
 */
@Service
public class RefreshTokenService {

    // Sliding: every refresh starts a new 30 days, so someone who opens the app at least once a month stays logged in
    static final Duration TTL = Duration.ofDays(30);
    // A token reused this soon after it was traded is a retry (lost response, two tabs at once), not a theft
    static final Duration REUSE_GRACE = Duration.ofSeconds(30);

    public record Issued(String token, Instant expiresAt) {}

    public record Rotation(String userId, String familyId, Issued next) {}

    private final RefreshTokenRepository tokens;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public RefreshTokenService(RefreshTokenRepository tokens) {
        this(tokens, Clock.systemUTC());
    }

    RefreshTokenService(RefreshTokenRepository tokens, Clock clock) {
        this.tokens = tokens;
        this.clock = clock;
    }

    /** A new token in `familyId` (a fresh UUID for a new login). */
    @Transactional
    public Issued issue(String userId, String familyId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = clock.instant().plus(TTL);
        tokens.save(new RefreshTokenEntity(hash(raw), userId, familyId, expiresAt));
        return new Issued(raw, expiresAt);
    }

    /**
     * Trades `raw` for its successor. Throws 401 INVALID_REFRESH_TOKEN when it is unknown or expired, or when it was
     * already traded more than REUSE_GRACE ago; that last case also ends the whole family, since someone else has a copy.
     */
    @Transactional(noRollbackFor = AuxException.class)
    public Rotation rotate(String raw) {
        Instant now = clock.instant();
        RefreshTokenEntity token = raw == null ? null : tokens.findById(hash(raw)).orElse(null);
        if (token == null) throw invalid();
        if (token.isExpired(now)) {
            tokens.delete(token);
            throw invalid();
        }
        if (token.getUsedAt() != null && now.isAfter(token.getUsedAt().plus(REUSE_GRACE))) {
            tokens.deleteFamily(token.getFamilyId());
            throw invalid();
        }
        token.markUsed(now);
        tokens.save(token);
        return new Rotation(token.getUserId(), token.getFamilyId(), issue(token.getUserId(), token.getFamilyId()));
    }

    /** Logout: ends the session `raw` belongs to. Unknown tokens are ignored. */
    @Transactional
    public void revoke(String raw) {
        if (raw == null) return;
        tokens.findById(hash(raw)).ifPresent(token -> tokens.deleteFamily(token.getFamilyId()));
    }

    /** Ends every session of the user except `keepFamilyId` (null ends them all). */
    @Transactional
    public void revokeOthers(String userId, String keepFamilyId) {
        tokens.deleteOtherFamilies(userId, keepFamilyId);
    }

    @Scheduled(fixedRate = 60 * 60_000)
    @Transactional
    public void purgeExpired() {
        tokens.deleteExpired(clock.instant());
    }

    // The raw token is 256 random bits, so a plain SHA-256 is enough; no salt or slow hash needed
    static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // Not INVALID_TOKEN: that code means "the access token is bad, try refreshing"; this one means the session is over
    static AuxException invalid() {
        return new AuxException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Session expired; log in again");
    }
}

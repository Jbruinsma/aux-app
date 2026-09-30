package com.aux_app.entity;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One active OTP per user. Saving a new one for the same user replaces the old. Store a hash, never the raw code.
 * pendingEmail binds the code to its purpose: null for a password change, the new address for an email change.
 */
@Entity
@Table(name = "otp_codes")
public class OtpCodeEntity {

    @Id
    @Column(name = "user_id")
    private String userId;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "pending_email", length = 320)
    private String pendingEmail;

    protected OtpCodeEntity() {}

    public OtpCodeEntity(String userId, String codeHash, Duration ttl, String pendingEmail) {
        this.userId = userId;
        this.pendingEmail = pendingEmail;
        this.codeHash = codeHash;
        this.sentAt = Instant.now();
        this.expiresAt = sentAt.plus(ttl);
    }

    public boolean isExpired() { return Instant.now().isAfter(expiresAt); }
    public void recordFailedAttempt() { attempts++; }

    public String getUserId() { return userId; }
    public String getCodeHash() { return codeHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public int getAttempts() { return attempts; }
    public Instant getSentAt() { return sentAt; }
    public String getPendingEmail() { return pendingEmail; }
}

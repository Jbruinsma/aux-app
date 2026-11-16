package com.aux_app.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One refresh token, stored as a SHA-256 of the raw value. familyId groups the chain of tokens from one login. */
@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenEntity {

    @Id
    @Column(name = "token_hash")
    private String tokenHash;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "family_id", nullable = false)
    private String familyId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    // Set when the token is traded for a successor; null while it is still the family's current token
    @Column(name = "used_at")
    private Instant usedAt;

    protected RefreshTokenEntity() {}

    public RefreshTokenEntity(String tokenHash, String userId, String familyId, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.familyId = familyId;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(Instant now) { return !now.isBefore(expiresAt); }
    public void markUsed(Instant now) { if (usedAt == null) usedAt = now; }

    public String getTokenHash() { return tokenHash; }
    public String getUserId() { return userId; }
    public String getFamilyId() { return familyId; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
}

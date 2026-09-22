package com.aux.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Maps the `users` table (see schema: users)
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @Column(name = "user_id")
    private String userId;

    @Column(nullable = false, unique = true, length = 16)
    private String username;

    // BCrypt hash, stored as text (fits in 72 chars)
    @Column(name = "password_hash", nullable = false, length = 72)
    private String passwordHash;

    @Column(name = "profile_picture_url")
    private String profilePictureUrl;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected UserEntity() {}

    public UserEntity(String userId, String username, String passwordHash) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.profilePictureUrl = "/default_profile_picture.svg";
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getProfilePictureUrl() { return profilePictureUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

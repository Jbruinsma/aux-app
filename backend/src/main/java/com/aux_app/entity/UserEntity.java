package com.aux_app.entity;

import java.time.Instant;

import com.aux_app.dto.users.OnboardingStep;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    // BCrypt hash, stored as text (fits in 72 chars)
    @Column(name = "password_hash", nullable = false, length = 72)
    private String passwordHash;

    @Column(name = "profile_picture_url")
    private String profilePictureUrl;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "onboarding_step", nullable = false)
    private OnboardingStep onboardingStep;

    protected UserEntity() {}

    public UserEntity(
            String userId,
            String email,
            String passwordHash,
            OnboardingStep onboardingStep
    ) {
        this.userId = userId;
        this.email = email;
        this.username = generateBaseUsername(userId);
        this.passwordHash = passwordHash;
        this.profilePictureUrl = "/default_profile_picture.svg";
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.onboardingStep = onboardingStep;
    }

    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getProfilePictureUrl() { return profilePictureUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public OnboardingStep getOnboardingStep() { return onboardingStep; }

    public void setUsername(String username) {
        this.username = username;
        this.updatedAt = Instant.now();
    }

    public void setOnboardingStep(OnboardingStep onboardingStep) {
        this.onboardingStep = onboardingStep;
        this.updatedAt = Instant.now();
    }

    public void setProfilePictureUrl(String profilePictureUrl) {
        this.profilePictureUrl = profilePictureUrl;
        this.updatedAt = Instant.now();
    }

    private String generateBaseUsername(String userId) {
        String baseId = "aux_";
        String slice = userId.substring(0, Math.min(userId.length(), 12));
        return baseId + slice;
    }

}

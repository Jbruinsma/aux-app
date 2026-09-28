package com.aux_app.dto.users;

import io.swagger.v3.oas.annotations.media.Schema;

import com.aux_app.entity.UserEntity;

// Public view of a user: never includes the password hash
public record UserSummary(
        @Schema(example = "u_41x9") String userId,
        @Schema(example = "justin") String username,
        @Schema(example = "https://aux.justinabruinsma.com/pfp/66fa2.webp") String profilePictureUrl,
        @Schema(example = "https://aux.justinabruinsma.com/banner/66fa2.webp") String bannerUrl,
        @Schema(example = "USERNAME (1) | PFP (2) | DONE (3)") OnboardingStep onboardingStep
) {
    public static UserSummary of(UserEntity user) {
        return new UserSummary(
                user.getUserId(),
                user.getUsername(),
                user.getProfilePictureUrl(),
                user.getBannerUrl(),
                user.getOnboardingStep()
        );
    }
}

package com.aux_app.dto.users;

import io.swagger.v3.oas.annotations.media.Schema;

import com.aux_app.entity.UserEntity;

@Schema(description = "The signed-in user")
public record UserSummary(
        @Schema(description = "Public id of the user", example = "u_41x9Rb") String userId,
        @Schema(description = "Username; a generated one until the USERNAME onboarding step is done", example = "justin") String username,
        @Schema(description = "Profile picture URL", example = "https://aux.justinabruinsma.com/pfp/3f2b8c1e-8d4a-4c6e-9a51-2f0d7b9e6c11.webp") String profilePictureUrl,
        @Schema(description = "Banner URL", example = "https://aux.justinabruinsma.com/banner/3f2b8c1e-8d4a-4c6e-9a51-2f0d7b9e6c11.webp") String bannerUrl,
        @Schema(description = "The step the user must complete next; DONE when onboarding is finished", example = "USERNAME (1) | PFP (2) | DONE (3)") OnboardingStep onboardingStep
) {
    public static UserSummary of(UserEntity user) {
        return new UserSummary(
                user.getPublicId(),
                user.getUsername(),
                user.getProfilePictureUrl(),
                user.getBannerUrl(),
                user.getOnboardingStep()
        );
    }
}

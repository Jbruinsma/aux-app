package com.aux_app.dto.users;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Everything the caller's settings page needs")
public record UserSettings(
        @Schema(description = "The caller's email", example = "example@domain.com") String email,
        @Schema(description = "Username", example = "justin") String username,
        @Schema(description = "Profile picture URL", example = "https://aux.justinabruinsma.com/pfp/3f2b8c1e-8d4a-4c6e-9a51-2f0d7b9e6c11.webp") String profilePictureUrl,
        @Schema(description = "Banner URL", example = "https://aux.justinabruinsma.com/banner/3f2b8c1e-8d4a-4c6e-9a51-2f0d7b9e6c11.webp") String bannerUrl,
        @Schema(description = "Optional profile fields") ProfileDetails profileDetails
) {
}

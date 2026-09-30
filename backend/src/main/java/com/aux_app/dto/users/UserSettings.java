package com.aux_app.dto.users;

import io.swagger.v3.oas.annotations.media.Schema;

public record UserSettings(
        @Schema(example = "example@domain.com") String email,
        @Schema(example = "justin") String username,
        @Schema(example = "https://aux.justinabruinsma.com/pfp/3f2b8c1e-8d4a-4c6e-9a51-2f0d7b9e6c11.webp") String profilePictureUrl,
        @Schema(example = "https://aux.justinabruinsma.com/banner/3f2b8c1e-8d4a-4c6e-9a51-2f0d7b9e6c11.webp") String bannerUrl,
        ProfileDetails profileDetails
) {
}

package com.aux_app.dto.search;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A user found by search")
public record UserSearchResult(
        @Schema(description = "Public id of the user", example = "u_41x9Rb") String userId,
        @Schema(description = "Username", example = "justin") String username,
        @Schema(description = "Profile picture URL", example = "https://aux.justinabruinsma.com/pfp/3f2b8c1e-8d4a-4c6e-9a51-2f0d7b9e6c11.webp") String profilePictureUrl
) {}

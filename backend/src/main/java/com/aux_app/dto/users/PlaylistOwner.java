package com.aux_app.dto.users;

import io.swagger.v3.oas.annotations.media.Schema;

public record PlaylistOwner(
        @Schema(example = "u_41x9Rb") String userId,
        @Schema(example = "https://aux.justinabruinsma.com/pfp/3f2b8c1e-8d4a-4c6e-9a51-2f0d7b9e6c11.webp") String pfpUrl,
        @Schema(example = "justin") String username
) {
}

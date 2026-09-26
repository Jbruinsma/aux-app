package com.aux_app.dto.users;

import io.swagger.v3.oas.annotations.media.Schema;

public record PlaylistOwner(
        @Schema(example = "u_41x9") String userId,
        @Schema(example = "/uploads/users/u_41x9.jpg") String pfpUrl,
        @Schema(example = "justin") String username
) {
}

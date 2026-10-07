package com.aux_app.dto.playlist;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "An accepted member who can edit the playlist's tracks")
public record PlaylistEditor(
        @Schema(description = "Public id of the editor", example = "u_41x9Rb") String userId,
        @Schema(description = "Username of the editor", example = "justin") String username,
        @Schema(description = "Profile picture URL", example = "https://aux.justinabruinsma.com/pfp/3f2b8c1e-8d4a-4c6e-9a51-2f0d7b9e6c11.webp", nullable = true) String pfpUrl
) {
}

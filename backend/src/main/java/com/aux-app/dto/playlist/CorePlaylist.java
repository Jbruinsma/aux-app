package com.aux.dto.playlist;

import io.swagger.v3.oas.annotations.media.Schema;

public record CorePlaylist(
        @Schema(example = "p_7c2d1") String playlistId,
        @Schema(example = "Late Night Drive") String playlistName,
        @Schema(example = "/uploads/playlists/p_7c2d1.jpg") String playlistCoverUrl
) {
}

package com.aux_app.dto.playlist;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The basic identity of a playlist")
public record CorePlaylist(
        @Schema(description = "Public id of the playlist", example = "p_7c2dK1") String playlistId,
        @Schema(description = "Display name, 1-36 chars", example = "Late Night Drive") String playlistName,
        @Schema(description = "Square WebP cover, at most 1024x1024", example = "https://aux.justinabruinsma.com/playlist-cover/3f2b8c1e-8d4a-4c6e-9a51-2f0d7b9e6c11.webp") String playlistCoverUrl
) {
}

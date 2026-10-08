package com.aux_app.dto.search;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A playlist found by search")
public record PlaylistSearchResult(
        @Schema(description = "Public id of the playlist", example = "p_7c2dK1") String playlistId,
        @Schema(description = "Playlist name", example = "Late Night Drive") String playlistName,
        @Schema(description = "Square WebP cover URL", example = "https://aux.justinabruinsma.com/playlist-cover/3f2b8c1e-8d4a-4c6e-9a51-2f0d7b9e6c11.webp") String playlistCoverUrl,
        @Schema(description = "Username of the playlist owner", example = "justin") String ownerUsername,
        @Schema(description = "Number of tracks in the playlist", example = "5") Integer pieceCount
) {}

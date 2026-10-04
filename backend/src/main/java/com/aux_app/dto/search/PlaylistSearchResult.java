package com.aux_app.dto.search;

import io.swagger.v3.oas.annotations.media.Schema;

public record PlaylistSearchResult(
        @Schema(example = "p_7c2d1") String playlistId,
        @Schema(example = "Late Night Drive") String playlistName,
        @Schema(example = "https://aux.justinabruinsma.com/playlist-cover/3f2b8c1e-8d4a-4c6e-9a51-2f0d7b9e6c11.webp") String playlistCoverUrl,
        @Schema(example = "justin") String ownerUsername,
        @Schema(example = "5") Integer pieceCount
) {}

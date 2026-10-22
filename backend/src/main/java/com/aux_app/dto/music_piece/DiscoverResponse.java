package com.aux_app.dto.music_piece;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Music pieces like a given piece")
public record DiscoverResponse(
        @Schema(description = "The piece the results are like") MusicPieceOverview seed,
        @Schema(description = "Up to 100 pieces, best match first; empty if nothing fits") List<MusicPieceOverview> musicPieces
) {}

package com.aux_app.dto.music_piece;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "One page of music pieces")
public record MusicPiecePage(
        @Schema(description = "This page's pieces, in order") List<MusicPieceOverview> musicPieces,
        @Schema(description = "Send back as `cursor` to get the next page; null on the last page", nullable = true) String nextCursor
) {}

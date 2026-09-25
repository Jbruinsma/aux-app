package com.aux.dto.music_piece;

import io.swagger.v3.oas.annotations.media.Schema;

import com.aux.dto.artist.ArtistSummary;

public record MusicPieceOverview(
        @Schema(example = "m_92kd0") String musicPieceId,
        @Schema(example = "Let It Happen") String name,
        @Schema(example = "/uploads/covers/m_92kd0.jpg") String coverUrl,
        ArtistSummary artistSummary,
        @Schema(example = "true") boolean isFavorite
) {
}

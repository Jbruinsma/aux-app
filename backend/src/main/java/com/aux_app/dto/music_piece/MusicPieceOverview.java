package com.aux_app.dto.music_piece;

import io.swagger.v3.oas.annotations.media.Schema;

import com.aux_app.dto.artist.ArtistSummary;

public record MusicPieceOverview(
        @Schema(example = "m_92kd0Z") String musicPieceId,
        @Schema(example = "Let It Happen") String name,
        @Schema(example = "https://aux.justinabruinsma.com/music-cover/3f2b8c1e-8d4a-4c6e-9a51-2f0d7b9e6c11.webp") String coverUrl,
        ArtistSummary artistSummary,
        @Schema(example = "true") boolean isFavorite
) {
}

package com.aux_app.dto.music_piece;

import io.swagger.v3.oas.annotations.media.Schema;

import com.aux_app.dto.artist.ArtistSummary;

@Schema(description = "A music piece as shown in a track list")
public record MusicPieceOverview(
        @Schema(description = "Public id of the music piece", example = "m_92kd0Z") String musicPieceId,
        @Schema(description = "Title", example = "Let It Happen") String name,
        @Schema(description = "Square WebP cover URL", example = "https://aux.justinabruinsma.com/music-cover/3f2b8c1e-8d4a-4c6e-9a51-2f0d7b9e6c11.webp") String coverUrl,
        @Schema(description = "The artist") ArtistSummary artistSummary,
        @Schema(description = "Whether the caller has favorited the piece", example = "true") boolean isFavorite
) {
}

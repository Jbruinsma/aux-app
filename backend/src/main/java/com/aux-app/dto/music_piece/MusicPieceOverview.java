package com.aux.dto.music_piece;

import com.aux.dto.artist.ArtistSummary;

public record MusicPieceOverview(
        String musicPieceId,
        String name,
        String coverUrl,
        ArtistSummary artistSummary,
        boolean isFavorite
) {
}

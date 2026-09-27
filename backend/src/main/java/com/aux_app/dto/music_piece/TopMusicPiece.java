package com.aux_app.dto.music_piece;

import com.aux_app.dto.artist.ArtistSummary;
import io.swagger.v3.oas.annotations.media.Schema;

public record TopMusicPiece(
        @Schema(example = "Devil In a New Dress") String name,
        @Schema(example = "/uploads/covers/m_92kd0.jpg") String coverUrl,
        ArtistSummary artistSummary
) {
}

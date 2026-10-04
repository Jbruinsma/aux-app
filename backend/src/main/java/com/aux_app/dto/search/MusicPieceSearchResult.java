package com.aux_app.dto.search;

import com.aux_app.dto.artist.ArtistSummary;
import io.swagger.v3.oas.annotations.media.Schema;

public record MusicPieceSearchResult(
        @Schema(example = "m_92kd0") String musicPieceId,
        @Schema(example = "Let It Happen") String name,
        @Schema(example = "https://aux.justinabruinsma.com/music-cover/3f2b8c1e-8d4a-4c6e-9a51-2f0d7b9e6c11.webp") String coverUrl,
        ArtistSummary artistSummary
) {}

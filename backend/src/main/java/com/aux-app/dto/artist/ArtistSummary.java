package com.aux.dto.artist;

import io.swagger.v3.oas.annotations.media.Schema;

public record ArtistSummary(
        @Schema(example = "a_8f3k2") String artistId,
        @Schema(example = "Tame Impala") String artistName,
        @Schema(example = "/uploads/artists/a_8f3k2.jpg") String artistPfpUrl
) {
}

package com.aux_app.dto.artist;

import io.swagger.v3.oas.annotations.media.Schema;

public record ArtistDetails(
        @Schema(example = "a_8f3k2Q") String artistId,
        @Schema(example = "Tame Impala") String artistName,
        @Schema(example = "https://aux.justinabruinsma.com/artist-pfp/3f2b8c1e-8d4a-4c6e-9a51-2f0d7b9e6c11.webp") String artistPfpUrl,
        @Schema(example = "https://aux.justinabruinsma.com/artist-banner/3f2b8c1e-8d4a-4c6e-9a51-2f0d7b9e6c11.webp") String artistBannerUrl,
        @Schema(example = "true") boolean isFavorite,
        @Schema(example = "false") boolean canEdit
) {
}

package com.aux_app.dto.artist;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "An artist's page data and the caller's relation to it")
public record ArtistDetails(
        @Schema(description = "Public id of the artist", example = "a_8f3k2Q") String artistId,
        @Schema(description = "Artist name", example = "Tame Impala") String artistName,
        @Schema(description = "Artist profile picture URL; null until set", example = "https://aux.justinabruinsma.com/artist-pfp/3f2b8c1e-8d4a-4c6e-9a51-2f0d7b9e6c11.webp") String artistPfpUrl,
        @Schema(description = "Artist banner URL; null until set", example = "https://aux.justinabruinsma.com/artist-banner/3f2b8c1e-8d4a-4c6e-9a51-2f0d7b9e6c11.webp") String artistBannerUrl,
        @Schema(description = "Whether the caller has favorited the artist; false with no token", example = "true") boolean isFavorite,
        @Schema(description = "Whether the caller created the artist and may change its pfp and banner; false with no token", example = "false") boolean canEdit
) {
}

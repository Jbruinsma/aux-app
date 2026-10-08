package com.aux_app.dto.artist;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The short form of an artist, shown on cards and next to music pieces")
public record ArtistSummary(
        @Schema(description = "Public id of the artist", example = "a_8f3k2Q") String artistId,
        @Schema(description = "Artist name", example = "Tame Impala") String artistName,
        @Schema(description = "Artist profile picture URL; null until set", example = "https://aux.justinabruinsma.com/artist-pfp/3f2b8c1e-8d4a-4c6e-9a51-2f0d7b9e6c11.webp") String artistPfpUrl
) {
}

package com.aux_app.dto.users;

import com.aux_app.dto.artist.ArtistSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "The caller's most played artists")
public record TopArtistsResponse(
        @Schema(description = "Up to 4 artists, most played first") List<ArtistSummary> artists
) {
}

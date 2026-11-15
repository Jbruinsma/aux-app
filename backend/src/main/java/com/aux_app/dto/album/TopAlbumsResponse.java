package com.aux_app.dto.album;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "A user's most played albums")
public record TopAlbumsResponse(
        @Schema(description = "Up to 10 albums, most played first; empty if the user hasn't played any album's pieces") List<AlbumOverview> albums
) {
}

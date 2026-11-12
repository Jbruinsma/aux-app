package com.aux_app.dto.search;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "One page of search results")
public record SearchEntity<T>(
        @Schema(description = "All matches for the query, not just this page", example = "10") Integer totalResults,
        @ArraySchema(
                arraySchema = @Schema(description = "This page of results, sorted by name. Every item has the same shape, picked by `category`: `UserSearchResult` for `USERS`, `MusicPieceSearchResult` for `MUSIC`, `PlaylistSearchResult` for `PLAYLISTS`"),
                schema = @Schema(oneOf = {UserSearchResult.class, MusicPieceSearchResult.class, PlaylistSearchResult.class})
        ) List<T> results
) {
}

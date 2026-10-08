package com.aux_app.dto.search;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "One page of search results")
public record SearchEntity<T>(
        @Schema(description = "All matches for the query, not just this page", example = "10") Integer totalResults,
        @Schema(description = "This page of results, sorted by name") List<T> results
) {
}

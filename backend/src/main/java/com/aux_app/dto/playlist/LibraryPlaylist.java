package com.aux_app.dto.playlist;

import io.swagger.v3.oas.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

public record LibraryPlaylist(
        @JsonUnwrapped CorePlaylist corePlaylist,
        @Schema(example = "justin") String ownerUsername,
        @Schema(example = "12") int totalPieces,
        @Schema(example = "true") boolean isSaved
) {}

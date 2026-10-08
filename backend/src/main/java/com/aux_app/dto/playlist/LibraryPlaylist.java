package com.aux_app.dto.playlist;

import io.swagger.v3.oas.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

@Schema(description = "A playlist in the caller's library")
public record LibraryPlaylist(
        @JsonUnwrapped CorePlaylist corePlaylist,
        @Schema(description = "Username of the playlist owner", example = "justin") String ownerUsername,
        @Schema(description = "Number of tracks in the playlist", example = "12") int totalPieces,
        @Schema(description = "Whether the caller has saved the playlist; false for the caller's own unsaved playlists", example = "true") boolean isSaved
) {}

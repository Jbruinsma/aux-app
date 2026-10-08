package com.aux_app.dto.playlist;

import io.swagger.v3.oas.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

@Schema(description = "A playlist as listed on a profile")
public record ProfilePlaylist(
        @JsonUnwrapped CorePlaylist corePlaylist,
        @Schema(description = "Number of tracks in the playlist", example = "12") int totalPieces
) {}

package com.aux_app.dto.playlist;

import io.swagger.v3.oas.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

public record ProfilePlaylist(
        @JsonUnwrapped CorePlaylist corePlaylist,
        @Schema(example = "12") int totalPieces
) {}

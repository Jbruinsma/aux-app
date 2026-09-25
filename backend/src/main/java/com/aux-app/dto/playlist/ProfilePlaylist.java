package com.aux.dto.playlist;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

public record ProfilePlaylist(
        @JsonUnwrapped CorePlaylist corePlaylist,
        int totalPieces
) {}

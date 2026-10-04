package com.aux_app.dto.playlist;

import io.swagger.v3.oas.annotations.media.Schema;

public record SavedPlaylistResponse(
        CorePlaylist corePlaylist,
        @Schema(example = "true (ALWAYS RETURNS TRUE)") Boolean isSaved
) {}

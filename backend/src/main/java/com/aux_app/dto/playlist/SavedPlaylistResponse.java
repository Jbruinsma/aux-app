package com.aux_app.dto.playlist;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of saving or unsaving a playlist")
public record SavedPlaylistResponse(
        @Schema(description = "The playlist that was saved or unsaved") CorePlaylist corePlaylist,
        @Schema(description = "Whether the playlist is saved after the call: true from save, false from unsave", example = "true") Boolean isSaved
) {}

package com.aux_app.dto.playlist;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of adding music pieces to a playlist")
public record PlaylistMusicPieceAdditionResponse(
        @Schema(description = "The playlist the pieces were added to") CorePlaylist corePlaylist,
        @Schema(description = "How many pieces were added", example = "3") int addedCount,
        @Schema(description = "How many requested pieces were skipped: unknown id, already in the playlist, a private piece the caller can't add here, or the playlist hit 10,000 tracks", example = "1") int failedCount,
        @Schema(description = "Pieces in the playlist after the call", example = "15") int totalPieces
) {}

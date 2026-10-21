package com.aux_app.dto.users;

import io.swagger.v3.oas.annotations.media.Schema;

import com.aux_app.dto.music_piece.MusicPieceOverview;

// Flat on purpose: the player already reads musicPiece and playlistId off this body
@Schema(description = "What the player needs on load: the last music piece the caller played and their play preferences")
public record LastPlaybackResponse(
        @Schema(description = "The music piece, or null if the caller has never played anything they can still play") MusicPieceOverview musicPiece,
        @Schema(description = "Public id of the playlist it was played from, or null", example = "p_8sK2mQ") String playlistId,
        @Schema(description = "Shuffle and repeat; the defaults if never saved") PlayPreferences playPreferences
) {}

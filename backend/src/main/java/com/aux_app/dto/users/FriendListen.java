package com.aux_app.dto.users;

import com.aux_app.dto.music_piece.MusicPieceOverview;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "A piece a friend is playing or played")
public record FriendListen(
        @Schema(description = "The music piece, always public. `isFavorite` is whether the caller favorited it") MusicPieceOverview musicPiece,
        @Schema(description = "For `nowPlaying`, when the piece started (moved by seeks, so now minus this is the position); for `lastPlayed`, when the counted play was recorded", example = "2026-11-13T14:05:12Z") Instant at
) {}

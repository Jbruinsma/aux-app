package com.aux_app.dto.users;

import io.swagger.v3.oas.annotations.media.Schema;

import com.aux_app.dto.music_piece.MusicPieceOverview;

@Schema(description = "The last music piece the caller played")
public record LastPlayback(
        @Schema(description = "The music piece") MusicPieceOverview musicPiece,
        @Schema(description = "Public id of the playlist it was played from, or null", example = "p_8sK2mQ") String playlistId,
        @Schema(description = "Username of that playlist's owner, for the link /playlist/{ownerUsername}/{playlistId}; null when playlistId is null", example = "justin") String playlistOwnerUsername,
        @Schema(description = "That playlist's name; null when playlistId is null", example = "Road trip") String playlistName
) {}

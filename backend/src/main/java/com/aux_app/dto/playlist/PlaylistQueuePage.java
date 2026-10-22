package com.aux_app.dto.playlist;

import io.swagger.v3.oas.annotations.media.Schema;

import com.aux_app.dto.music_piece.MusicPieceOverview;

import java.util.List;

@Schema(description = "One page of a playlist's play queue")
public record PlaylistQueuePage(
        @Schema(description = "Next tracks, in order. Track-list pages include unavailable pieces (`isAvailable` false); the play queue leaves them out") List<MusicPieceOverview> musicPieces,
        @Schema(description = "Send back as `cursor` to get the next page; null when the queue is done", nullable = true) String nextCursor
) {}

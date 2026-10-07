package com.aux_app.dto.playlist;

import io.swagger.v3.oas.annotations.media.Schema;

import com.aux_app.dto.music_piece.MusicPieceOverview;
import com.aux_app.dto.users.PlaylistOwner;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.util.List;

public record PlaylistOverview(
        @JsonUnwrapped CorePlaylist playlist,
        @Schema(example = "12") int totalPieces,
        @Schema(example = "true") boolean isPublic,
        PlaylistOwner playlistOwner,
        @Schema(example = "false") boolean isSaved,
        List<MusicPieceOverview> musicPieces,
        @Schema(description = "Accepted members with EDITOR permission; empty for a new playlist") List<PlaylistEditor> editors
) {}

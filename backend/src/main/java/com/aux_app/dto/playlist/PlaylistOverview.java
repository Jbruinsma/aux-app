package com.aux_app.dto.playlist;

import io.swagger.v3.oas.annotations.media.Schema;

import com.aux_app.dto.music_piece.MusicPieceOverview;
import com.aux_app.dto.users.PlaylistOwner;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.util.List;

public record PlaylistOverview(
        @JsonUnwrapped CorePlaylist playlist,
        @Schema(example = "12") int totalPieces,
        PlaylistOwner playlistOwner,
        @Schema(example = "false") boolean isSaved,
        List<MusicPieceOverview> musicPieces
) {}

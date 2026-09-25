package com.aux.dto.playlist;

import com.aux.dto.music_piece.MusicPieceOverview;
import com.aux.dto.users.PlaylistOwner;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.util.List;

public record PlaylistOverview(
        @JsonUnwrapped CorePlaylist playlist,
        int totalPieces,
        PlaylistOwner playlistOwner,
        boolean isSaved,
        List<MusicPieceOverview> musicPieces
) {}

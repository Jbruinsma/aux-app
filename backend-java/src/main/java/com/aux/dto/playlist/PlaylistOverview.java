package com.aux.dto.playlist;

import com.aux.dto.music_piece.MusicPieceOverview;
import com.aux.dto.users.PlaylistOwner;

import java.util.List;

public record PlaylistOverview(
        String playlistId,
        String playlistCoverUrl,
        String playlistName,
        int totalPieces,
        PlaylistOwner playlistOwner,
        boolean isSaved,
        List<MusicPieceOverview> musicPieces
) {}

package com.aux_app.dto.playlist;

import io.swagger.v3.oas.annotations.media.Schema;

import com.aux_app.dto.music_piece.MusicPieceOverview;
import com.aux_app.dto.users.PlaylistOwner;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.util.List;

@Schema(description = "A playlist with its tracks, owner, editors and the caller's access")
public record PlaylistOverview(
        @JsonUnwrapped CorePlaylist playlist,
        @Schema(description = "Number of tracks in the playlist", example = "12") int totalPieces,
        @Schema(description = "Whether anyone can open the playlist; private ones are only for the owner and accepted members", example = "true") boolean isPublic,
        @Schema(description = "The playlist's owner") PlaylistOwner playlistOwner,
        @Schema(description = "Whether the caller has saved the playlist", example = "false") boolean isSaved,
        @Schema(description = "First page of tracks in playlist order; empty for a new playlist") List<MusicPieceOverview> musicPieces,
        @Schema(description = "Send as `cursor` to `GET /api/playlists/{playlistId}/pieces` for the next page; null when every track is here", nullable = true) String nextCursor,
        @Schema(description = "Accepted members with EDITOR permission; empty for a new playlist") List<PlaylistEditor> editors,
        @Schema(description = "Caller's access; null when they are only viewing a public playlist", nullable = true) PlaylistAccess access
) {}

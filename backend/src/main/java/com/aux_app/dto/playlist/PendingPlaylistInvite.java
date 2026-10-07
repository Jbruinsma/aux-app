package com.aux_app.dto.playlist;

import com.aux_app.dto.users.PlaylistOwner;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

@Schema(description = "An unanswered invite to join a playlist")
public record PendingPlaylistInvite(
        @Schema(description = "Public id of the playlist; send it to the accept and decline endpoints", example = "p_7c2dK1", nullable = true)
        @Nullable String playlistId,
        @Schema(description = "Name of the playlist", example = "Late Night Drive") String playlistName,
        @Schema(description = "Cover of the playlist", example = "https://aux.justinabruinsma.com/playlist-cover/3f2b8c1e-8d4a-4c6e-9a51-2f0d7b9e6c11.webp") String playlistCoverUrl,
        @Schema(description = "Access the invite grants once accepted") PlaylistPermission permission,
        @Schema(description = "The playlist owner who sent the invite") PlaylistOwner invitedBy
) {
}

package com.aux_app.dto.playlist;

import com.aux_app.dto.users.PlaylistOwner;
import jakarta.annotation.Nullable;

public record PendingPlaylistInvite(
        @Nullable String playlistId,
        String playlistName,
        String playlistCoverUrl,
        PlaylistPermission permission,
        PlaylistOwner invitedBy
) {
}

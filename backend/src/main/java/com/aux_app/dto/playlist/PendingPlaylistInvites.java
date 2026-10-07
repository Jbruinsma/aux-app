package com.aux_app.dto.playlist;

import java.util.List;

public record PendingPlaylistInvites(
        List<PendingPlaylistInvite> invites
) {
}

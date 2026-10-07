package com.aux_app.dto.playlist;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "The caller's pending playlist invites")
public record PendingPlaylistInvites(
        @Schema(description = "Newest first; empty if there are none") List<PendingPlaylistInvite> invites
) {
}

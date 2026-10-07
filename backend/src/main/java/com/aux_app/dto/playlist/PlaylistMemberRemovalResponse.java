package com.aux_app.dto.playlist;

import com.aux_app.dto.users.PlaylistOwner;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The membership that was removed")
public record PlaylistMemberRemovalResponse(
        @Schema(description = "The removed user") PlaylistOwner user,
        @Schema(description = "Status the membership had when removed: PENDING means an invite was revoked") PlaylistMemberStatus previousStatus
) {
}

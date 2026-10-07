package com.aux_app.dto.playlist;

import com.aux_app.dto.users.PlaylistOwner;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "A user's membership row on a playlist")
public record PlaylistMemberResponse(
        @Schema(description = "The invited or member user") PlaylistOwner user,
        @Schema(description = "LISTENER can only play; EDITOR can also change tracks") PlaylistPermission permission,
        @Schema(description = "PENDING until the user accepts the invite") PlaylistMemberStatus status,
        @Schema(description = "When the invite was sent", example = "2026-10-06T14:03:11Z") Instant invitedAt,
        @Schema(description = "When the user accepted; null while PENDING", example = "2026-10-07T09:12:45Z", nullable = true) Instant respondedAt
) {
    public static PlaylistMemberResponse of(
            com.aux_app.entity.PlaylistMemberEntity m,
            com.aux_app.entity.UserEntity u
    ) {
        return new PlaylistMemberResponse(
                new PlaylistOwner(u.getPublicId(), u.getProfilePictureUrl(), u.getUsername()),
                m.getPermission(), m.getStatus(), m.getInvitedAt(), m.getRespondedAt()
        );
    }
}

package com.aux_app.dto.notification;

import java.time.Instant;

import com.aux_app.dto.playlist.CorePlaylist;
import com.aux_app.dto.playlist.PlaylistPermission;
import com.aux_app.dto.users.PlaylistOwner;
import com.aux_app.repository.NotificationRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@Schema(description = "One entry in the caller's notifications")
public record NotificationResponse(
        @Schema(description = "Public id; send it to PUT /api/notifications/read", example = "n_4Gk2Zq") String notificationId,
        NotificationType type,
        @Schema(description = "Who did it: the inviter, the user who accepted, or the user who added songs") PlaylistOwner actor,
        @Schema(description = "The playlist it's about") CorePlaylist playlist,
        @Schema(description = "Username of the playlist's owner, for the link /playlist/{ownerUsername}/{playlistId}", example = "justin") String playlistOwnerUsername,
        @Schema(description = "Access the invite grants; PLAYLIST_INVITE only", nullable = true) @Nullable PlaylistPermission permission,
        @Schema(description = "How many songs were added; TRACKS_ADDED only", example = "3", nullable = true) @Nullable Integer trackCount,
        boolean isRead,
        Instant createdAt
) {

    public static NotificationResponse of(NotificationRepository.NotificationWithDetails row) {
        var n = row.notification();
        var actor = row.actor();
        var playlist = row.playlist();
        return new NotificationResponse(
                n.getPublicId(),
                n.getType(),
                new PlaylistOwner(actor.getPublicId(), actor.getProfilePictureUrl(), actor.getUsername()),
                new CorePlaylist(playlist.getPublicId(), playlist.getPlaylistName(), playlist.getPlaylistCoverUrl()),
                row.owner().getUsername(),
                n.getPermission(),
                n.getTrackCount(),
                n.getIsRead(),
                n.getCreatedAt()
        );
    }
}

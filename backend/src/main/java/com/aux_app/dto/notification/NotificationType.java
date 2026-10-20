package com.aux_app.dto.notification;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
        PLAYLIST_INVITE: someone invited the caller to a playlist. INVITE_ACCEPTED: someone accepted the caller's invite.
        TRACKS_ADDED: someone else added songs to a playlist the caller owns or is a member of.""")
public enum NotificationType {
    PLAYLIST_INVITE,
    INVITE_ACCEPTED,
    TRACKS_ADDED
}

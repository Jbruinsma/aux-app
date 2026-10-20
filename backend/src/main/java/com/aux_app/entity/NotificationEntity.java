package com.aux_app.entity;

import java.time.Instant;
import java.util.UUID;

import com.aux_app.dto.notification.NotificationType;
import com.aux_app.dto.playlist.PlaylistPermission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Maps the `notifications` table. Every type is about a playlist; `permission` is only set on PLAYLIST_INVITE,
// `trackCount` only on TRACKS_ADDED. Rows go away with their recipient, actor or playlist (ON DELETE CASCADE).
@Entity
@Table(name = "notifications")
public class NotificationEntity {

    @Id
    @Column(name = "notification_id", nullable = false, length = 36)
    private String notificationId;

    // What the API shows instead of the UUID; see PublicId
    @Column(name = "public_id", nullable = false, unique = true, length = 8)
    private String publicId;

    @Column(name = "recipient_id", nullable = false)
    private String recipientId;

    @Column(name = "actor_id", nullable = false)
    private String actorId;

    @Column(name = "playlist_id", nullable = false)
    private String playlistId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission")
    private PlaylistPermission permission;

    @Column(name = "track_count")
    private Integer trackCount;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected NotificationEntity() {}

    private NotificationEntity(
            String recipientId,
            String actorId,
            String playlistId,
            NotificationType type,
            PlaylistPermission permission,
            Integer trackCount
    ) {
        this.notificationId = UUID.randomUUID().toString();
        this.publicId = PublicId.generate(PublicId.NOTIFICATION);
        this.recipientId = recipientId;
        this.actorId = actorId;
        this.playlistId = playlistId;
        this.type = type;
        this.permission = permission;
        this.trackCount = trackCount;
        this.isRead = false;
        this.createdAt = Instant.now();
    }

    // All ids are internal ones, not public ones
    public static NotificationEntity playlistInvite(
            String inviteeId, String inviterId, String playlistId, PlaylistPermission permission) {
        return new NotificationEntity(inviteeId, inviterId, playlistId, NotificationType.PLAYLIST_INVITE, permission, null);
    }

    public static NotificationEntity inviteAccepted(String ownerId, String acceptedById, String playlistId) {
        return new NotificationEntity(ownerId, acceptedById, playlistId, NotificationType.INVITE_ACCEPTED, null, null);
    }

    public static NotificationEntity tracksAdded(String recipientId, String addedById, String playlistId, int trackCount) {
        return new NotificationEntity(recipientId, addedById, playlistId, NotificationType.TRACKS_ADDED, null, trackCount);
    }

    public String getNotificationId() { return notificationId; }
    public String getPublicId() { return publicId; }
    public String getRecipientId() { return recipientId; }
    public String getActorId() { return actorId; }
    public String getPlaylistId() { return playlistId; }
    public NotificationType getType() { return type; }
    public PlaylistPermission getPermission() { return permission; }
    public Integer getTrackCount() { return trackCount; }
    public Boolean getIsRead() { return isRead; }
    public Instant getCreatedAt() { return createdAt; }
}

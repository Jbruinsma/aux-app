package com.aux_app.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import com.aux_app.dto.playlist.PlaylistPermission;
import com.aux_app.entity.NotificationEntity;
import com.aux_app.entity.PlaylistEntity;
import com.aux_app.entity.UserEntity;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

// All ids here are internal ones except `publicIds` in markRead
public interface NotificationRepository extends JpaRepository<NotificationEntity, String> {

    // One page of a user's notifications, newest first, with actor and playlist. Keyset on (createdAt, notificationId):
    // pass the last row of the previous page, or nulls for the first page. Rows deleted between pages don't shift it.
    default List<NotificationWithDetails> findPage(
            String recipientId, @Nullable Instant afterCreatedAt, @Nullable String afterNotificationId, int limit) {
        return afterCreatedAt == null
                ? findFirstPage(recipientId, Limit.of(limit))
                : findPageAfter(recipientId, afterCreatedAt, afterNotificationId, Limit.of(limit));
    }

    @Query("""
            SELECT new com.aux_app.repository.NotificationRepository$NotificationWithDetails(n, a, p, o)
            FROM NotificationEntity n
            JOIN UserEntity a ON a.userId = n.actorId
            JOIN PlaylistEntity p ON p.playlistId = n.playlistId
            JOIN UserEntity o ON o.userId = p.ownerId
            WHERE n.recipientId = :recipientId
            ORDER BY n.createdAt DESC, n.notificationId DESC
            """)
    List<NotificationWithDetails> findFirstPage(@Param("recipientId") String recipientId, Limit limit);

    @Query("""
            SELECT new com.aux_app.repository.NotificationRepository$NotificationWithDetails(n, a, p, o)
            FROM NotificationEntity n
            JOIN UserEntity a ON a.userId = n.actorId
            JOIN PlaylistEntity p ON p.playlistId = n.playlistId
            JOIN UserEntity o ON o.userId = p.ownerId
            WHERE n.recipientId = :recipientId
              AND (n.createdAt < :afterCreatedAt
                   OR (n.createdAt = :afterCreatedAt AND n.notificationId < :afterNotificationId))
            ORDER BY n.createdAt DESC, n.notificationId DESC
            """)
    List<NotificationWithDetails> findPageAfter(
            @Param("recipientId") String recipientId,
            @Param("afterCreatedAt") Instant afterCreatedAt,
            @Param("afterNotificationId") String afterNotificationId,
            Limit limit);

    // `owner` is the playlist's owner, for the link to it (the actor isn't always the owner)
    record NotificationWithDetails(NotificationEntity notification, UserEntity actor, PlaylistEntity playlist, UserEntity owner) {}

    long countByRecipientIdAndIsReadFalse(String recipientId);

    // Ids that aren't the recipient's match nothing, so they're ignored
    @Transactional
    @Modifying
    @Query("""
            UPDATE NotificationEntity n SET n.isRead = true
            WHERE n.recipientId = :recipientId AND n.publicId IN :publicIds
            """)
    void markRead(@Param("recipientId") String recipientId, @Param("publicIds") Collection<String> publicIds);

    @Transactional
    @Modifying
    @Query("UPDATE NotificationEntity n SET n.isRead = true WHERE n.recipientId = :recipientId AND n.isRead = false")
    void markAllRead(@Param("recipientId") String recipientId);

    // Called when an invite is accepted, so the bell never offers Accept on a gone invite
    @Transactional
    @Modifying
    @Query("""
            DELETE FROM NotificationEntity n
            WHERE n.recipientId = :recipientId AND n.playlistId = :playlistId
              AND n.type = com.aux_app.dto.notification.NotificationType.PLAYLIST_INVITE
            """)
    void deleteInvite(@Param("recipientId") String recipientId, @Param("playlistId") String playlistId);

    // Called when the recipient declines, leaves or is removed: they lose the playlist, so its notifications go too.
    // Otherwise they'd keep seeing its current name and cover
    @Transactional
    @Modifying
    @Query("DELETE FROM NotificationEntity n WHERE n.recipientId = :recipientId AND n.playlistId = :playlistId")
    void deleteForPlaylist(@Param("recipientId") String recipientId, @Param("playlistId") String playlistId);

    // The owner changed a pending invite's permission; keeps the invite notification in step with playlist_members
    @Transactional
    @Modifying
    @Query("""
            UPDATE NotificationEntity n SET n.permission = :permission
            WHERE n.recipientId = :recipientId AND n.playlistId = :playlistId
              AND n.type = com.aux_app.dto.notification.NotificationType.PLAYLIST_INVITE
            """)
    void updateInvitePermission(
            @Param("recipientId") String recipientId,
            @Param("playlistId") String playlistId,
            @Param("permission") PlaylistPermission permission);
}

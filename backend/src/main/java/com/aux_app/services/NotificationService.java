package com.aux_app.services;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.aux_app.dto.notification.NotificationPage;
import com.aux_app.dto.notification.NotificationResponse;
import com.aux_app.error.AuxException;
import com.aux_app.repository.NotificationRepository;
import com.aux_app.repository.NotificationRepository.NotificationWithDetails;
import com.aux_app.services.LiveEventService.Section;

// Reading and marking notifications. Rows are written where the event happens (PlaylistMembersService for invites,
// PlaylistService for added tracks), each followed by a NOTIFICATIONS live update to the recipient.
@Service
public class NotificationService {

    static final int PAGE_SIZE = 20;

    private final NotificationRepository notifications;
    private final ApplicationEventPublisher events;

    public NotificationService(NotificationRepository notifications, ApplicationEventPublisher events) {
        this.notifications = notifications;
        this.events = events;
    }

    // Also the payload of the `notifications` live event (first page, cursor null)
    public NotificationPage page(String userId, @Nullable String cursor) {
        Instant afterCreatedAt = null;
        String afterNotificationId = null;
        if (cursor != null) {
            Cursor decoded = decodeCursor(cursor);
            afterCreatedAt = decoded.createdAt();
            afterNotificationId = decoded.notificationId();
        }

        // One extra row tells whether there's a next page without a count query
        List<NotificationWithDetails> rows = notifications.findPage(userId, afterCreatedAt, afterNotificationId, PAGE_SIZE + 1);
        boolean more = rows.size() > PAGE_SIZE;
        List<NotificationWithDetails> shown = more ? rows.subList(0, PAGE_SIZE) : rows;

        return new NotificationPage(
                shown.stream().map(NotificationResponse::of).toList(),
                notifications.countByRecipientIdAndIsReadFalse(userId),
                more ? encodeCursor(shown.getLast()) : null
        );
    }

    public void markRead(String userId, Collection<String> publicIds) {
        notifications.markRead(userId, publicIds);
        // Other tabs update their badge
        publish(userId);
    }

    public void markAllRead(String userId) {
        notifications.markAllRead(userId);
        publish(userId);
    }

    private void publish(String userId) {
        events.publishEvent(new LiveEventService.Update(Set.of(userId), Set.of(Section.NOTIFICATIONS)));
    }

    // Keyset cursor: the last row's createdAt and internal id, as the repository pages on. Base64 so clients
    // treat it as opaque. Instant.toString() keeps full precision, so the `=` tie-break in the query still matches.
    record Cursor(Instant createdAt, String notificationId) {}

    static String encodeCursor(NotificationWithDetails last) {
        String raw = last.notification().getCreatedAt() + "|" + last.notification().getNotificationId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    static Cursor decodeCursor(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", -1);
            if (parts.length == 2 && !parts[1].isEmpty()) {
                return new Cursor(Instant.parse(parts[0]), parts[1]);
            }
        } catch (IllegalArgumentException | DateTimeException ignored) {
            // Bad base64 or timestamp; falls through to the 400
        }
        throw new AuxException(HttpStatus.BAD_REQUEST, "INVALID_FIELD", "Invalid cursor", "cursor");
    }
}

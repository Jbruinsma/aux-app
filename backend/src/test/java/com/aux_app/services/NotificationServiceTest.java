package com.aux_app.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.aux_app.dto.playlist.PlaylistPermission;
import com.aux_app.entity.NotificationEntity;
import com.aux_app.error.AuxException;
import com.aux_app.repository.NotificationRepository.NotificationWithDetails;

class NotificationServiceTest {

    @Test
    void cursorRoundTripsAtFullPrecision() {
        NotificationEntity n = NotificationEntity.playlistInvite("u1", "u2", "p1", PlaylistPermission.LISTENER);

        NotificationService.Cursor cursor = NotificationService.decodeCursor(
                NotificationService.encodeCursor(new NotificationWithDetails(n, null, null, null)));

        assertEquals(n.getCreatedAt(), cursor.createdAt());
        assertEquals(n.getNotificationId(), cursor.notificationId());
    }

    @Test
    void badCursorIs400() {
        for (String bad : new String[] {"!!!", "bm90LWEtY3Vyc29y", "eHx5"}) {
            assertThrows(AuxException.class, () -> NotificationService.decodeCursor(bad), bad);
        }
    }
}

package com.aux_app.controller;

import com.aux_app.auth.CurrentUser;
import com.aux_app.dto.notification.NotificationPage;
import com.aux_app.dto.notification.NotificationReadRequest;
import com.aux_app.entity.UserEntity;
import com.aux_app.services.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping("")
    @Operation(
            summary = "List the caller's notifications",
            description = """
                    Newest first, 20 per page, with `unreadCount` for the badge. Types: PLAYLIST_INVITE (accept or
                    decline it with the playlist invite endpoints; it disappears once answered), INVITE_ACCEPTED,
                    TRACKS_ADDED. Notifications about a playlist go away when it's deleted, or when the caller
                    declines, leaves or is removed from it.
                    The first page also arrives as the `notifications` event on GET /api/users/me/events, and again
                    whenever it changes. Path is `GET /api/notifications`, no trailing slash.
                    """)
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "Bad cursor (code INVALID_FIELD)")
    public NotificationPage getNotifications(
            @CurrentUser UserEntity user,
            @Parameter(description = "`nextCursor` from the previous page; omit for the first") @RequestParam(required = false) String cursor
    ) {
        return notifications.page(user.getUserId(), cursor);
    }

    @PutMapping("/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Mark notifications as read", description = "Already read, unknown, or other users' ids are ignored.")
    @ApiResponse(responseCode = "204", description = "Done")
    @ApiResponse(responseCode = "400", description = "No ids, or more than 100 (code INVALID_FIELD)")
    public void markRead(
            @CurrentUser UserEntity user,
            @Valid @RequestBody NotificationReadRequest request
    ) {
        notifications.markRead(user.getUserId(), request.notificationIds());
    }

    @PutMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Mark every notification as read")
    @ApiResponse(responseCode = "204", description = "Done")
    public void markAllRead(@CurrentUser UserEntity user) {
        notifications.markAllRead(user.getUserId());
    }
}

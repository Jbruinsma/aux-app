package com.aux_app.dto.notification;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@Schema(description = "One page of the caller's notifications, newest first")
public record NotificationPage(
        List<NotificationResponse> notifications,
        @Schema(description = "Unread notifications in total, not just on this page; the bell badge", example = "2") long unreadCount,
        @Schema(description = "Pass as `cursor` for the next page; null on the last page", nullable = true) @Nullable String nextCursor
) {}

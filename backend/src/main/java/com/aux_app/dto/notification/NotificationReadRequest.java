package com.aux_app.dto.notification;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

@Schema(description = "Notifications to mark as read")
public record NotificationReadRequest(
        @Schema(description = "Public ids, 1 to 100. Ids that aren't the caller's are ignored", example = "[\"n_4Gk2Zq\"]")
        @NotEmpty @Size(max = 100) List<String> notificationIds
) {}

package com.aux_app.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Starts an email change; a 6-digit code is sent to the new address")
public record EmailChangeRequest(
        @Schema(description = "New email address, 3-254 chars", example = "new@domain.com") @NotBlank @Size(min= 3, max= 254) String newEmail,
        @Schema(description = "The user's current password, 8-32 chars", example = "correct-horse-battery") @NotBlank @Size(min= 8, max= 32) String currentPassword
) {}

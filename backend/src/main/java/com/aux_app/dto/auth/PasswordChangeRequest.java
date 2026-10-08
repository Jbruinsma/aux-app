package com.aux_app.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Starts a password change; a 6-digit code is sent to the current address")
public record PasswordChangeRequest(
        @Schema(description = "The user's current password, 8-32 chars", example = "correct-horse-battery") @NotBlank @Size(min= 8, max= 32) String currentPassword
) {}

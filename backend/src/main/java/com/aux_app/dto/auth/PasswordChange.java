package com.aux_app.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Finishes a password change")
public record PasswordChange(
        @Schema(description = "6-digit code from the email", example = "123456") @NotBlank @Pattern(regexp = "\\d{6}") String code,
        @Schema(description = "New password, 8-32 chars and at most 72 bytes in UTF-8", example = "correct-horse-battery") @NotBlank @Size(min= 8, max= 32) String newPassword
) {}

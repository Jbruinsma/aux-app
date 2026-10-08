package com.aux_app.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Email and password of an existing account")
public record LoginCredentials(
        @Schema(description = "Account email, 3-254 chars", example = "example@domain.com") @NotBlank @Size(min= 3, max= 254) String email,
        @Schema(description = "Account password, 8-32 chars", example = "correct-horse-battery") @NotBlank @Size(min= 8, max= 32) String password
) {}

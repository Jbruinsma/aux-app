package com.aux_app.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Email and password for a new account")
public record RegistrationCredentials(
        @Schema(description = "Email for the new account, 3-254 chars", example = "example@domain.com") @NotBlank @Size(min= 3, max= 254) String email,
        @Schema(description = "Password, 8-32 chars and at most 72 bytes in UTF-8", example = "correct-horse-battery") @NotBlank @Size(min= 8, max= 32) String password
) {}

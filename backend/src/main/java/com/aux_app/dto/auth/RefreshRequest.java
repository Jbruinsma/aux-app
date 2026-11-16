package com.aux_app.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "A refresh token from register, login or a previous refresh")
public record RefreshRequest(
        @Schema(description = "The `refreshToken` from the last AuthResponse") @NotBlank @Size(max = 128) String refreshToken
) {}

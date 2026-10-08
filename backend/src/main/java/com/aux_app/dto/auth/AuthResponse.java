package com.aux_app.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

import com.aux_app.dto.users.UserSummary;

// Returned by register (and later login): send `token` as `Authorization: Bearer <token>`
@Schema(description = "Session returned by register and login")
public record AuthResponse(
        @Schema(description = "Signed JWT; send it as `Authorization: Bearer <token>`", example = "eyJhbGciOiJIUzI1NiJ9...") String token,
        @Schema(description = "When the token stops being valid (UTC)", example = "2026-10-25T12:00:00Z") Instant expiresAt,
        @Schema(description = "The signed-in user") UserSummary user
) {}

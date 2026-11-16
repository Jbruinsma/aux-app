package com.aux_app.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

import com.aux_app.dto.users.UserSummary;

// Returned by register, login and refresh: send `token` as `Authorization: Bearer <token>`, and trade `refreshToken`
// at POST /api/auth/refresh for a new pair before `expiresAt`
@Schema(description = "Session returned by register, login and refresh")
public record AuthResponse(
        @Schema(description = "Signed JWT; send it as `Authorization: Bearer <token>`", example = "eyJhbGciOiJIUzI1NiJ9...") String token,
        @Schema(description = "When the token stops being valid (UTC)", example = "2026-10-25T12:00:00Z") Instant expiresAt,
        @Schema(description = "Single-use token for POST /api/auth/refresh; each refresh returns a new one", example = "Q2xhdWRl...") String refreshToken,
        @Schema(description = "When the refresh token stops being valid (UTC); renewed on every refresh", example = "2026-11-24T11:00:00Z") Instant refreshExpiresAt,
        @Schema(description = "The signed-in user") UserSummary user
) {}

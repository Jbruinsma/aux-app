package com.aux.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

import com.aux.dto.users.UserSummary;

// Returned by register (and later login): send `token` as `Authorization: Bearer <token>`
public record AuthResponse(
        @Schema(example = "eyJhbGciOiJIUzI1NiJ9...") String token,
        @Schema(example = "2026-10-25T12:00:00Z") Instant expiresAt,
        UserSummary user
) {}

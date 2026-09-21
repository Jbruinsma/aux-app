package com.aux.dto.auth;

import java.time.Instant;

import com.aux.dto.users.UserSummary;

// Returned by register (and later login): send `token` as `Authorization: Bearer <token>`
public record AuthResponse(
        String token,
        Instant expiresAt,
        UserSummary user
) {}

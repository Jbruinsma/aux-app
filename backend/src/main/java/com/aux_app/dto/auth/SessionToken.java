package com.aux_app.dto.auth;

import java.time.Instant;

// A signed JWT plus when it stops being valid, so callers don't have to decode it
public record SessionToken(
        String token,
        Instant expiresAt
) {}

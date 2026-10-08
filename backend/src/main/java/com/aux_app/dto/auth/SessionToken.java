package com.aux_app.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

// A signed JWT plus when it stops being valid, so callers don't have to decode it
@Schema(description = "A signed JWT plus when it stops being valid")
public record SessionToken(
        @Schema(description = "Signed JWT") String token,
        @Schema(description = "When the token stops being valid (UTC)") Instant expiresAt
) {}

package com.aux_app.dto.base;

import io.swagger.v3.oas.annotations.media.Schema;
// Generic success body: {"message": "..."}
@Schema(description = "Generic success body")
public record Message(
        @Schema(description = "Human-readable result") String message
) {}

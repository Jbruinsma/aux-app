package com.aux_app.dto.base;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "Details of an error response")
public record ErrorDetails(
        @Schema(description = "HTTP reason phrase, e.g. `Not Found`") String error,
        @Schema(description = "Stable machine-readable error code, e.g. `PLAYLIST_NOT_FOUND`") String code,
        @Schema(description = "Human-readable explanation") String message,
        @Schema(description = "Name of the offending field or parameter; null when none applies", nullable = true) String parameter
) {}

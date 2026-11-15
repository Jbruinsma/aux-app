package com.aux_app.dto.imports;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Whether the caller can import from YouTube")
public record YoutubeImportAvailability(
        @Schema(description = "True when the feature is on and the caller is a tester or admin. Hide the YouTube option when false") boolean available
) {}

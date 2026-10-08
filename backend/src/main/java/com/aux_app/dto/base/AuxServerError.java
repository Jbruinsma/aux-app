package com.aux_app.dto.base;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "Body of every 4xx and 5xx response")
public record AuxServerError(
   @Schema(description = "What went wrong") ErrorDetails errorDetails
) {}

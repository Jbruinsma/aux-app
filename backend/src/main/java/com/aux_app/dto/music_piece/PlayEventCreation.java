package com.aux_app.dto.music_piece;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@Schema(description = "One listen of a music piece")
public record PlayEventCreation(
        @Schema(description = "The `playToken` from the `/stream` call that started this listen")
        @NotBlank String playToken,
        @Schema(description = "How long the caller listened, in seconds. The server caps it at the time since the token was issued and at the piece's length", example = "142")
        @Positive int listenDurationSeconds
) {}

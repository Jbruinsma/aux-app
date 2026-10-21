package com.aux_app.dto.users;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "The caller's player settings, kept across devices")
public record PlayPreferences(
        @Schema(description = "Shuffle on or off", example = "false")
        @NotNull
        Boolean shuffleEnabled,

        @Schema(description = "PLAYLIST repeats the queue, PIECE repeats the current piece, OFF stops when the queue ends", example = "OFF")
        @NotNull
        RepeatPreference repeatPreference
) {}

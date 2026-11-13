package com.aux_app.dto.users;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

@Schema(description = "What the caller's player is doing")
public record NowPlayingUpdate(
        @Schema(description = "Public id of the piece loaded in the player", example = "m_92kd0Z") @NotBlank String musicPieceId,
        @Schema(description = "How far into the piece the player is, in seconds", example = "42") @NotNull @PositiveOrZero Integer positionSeconds,
        @Schema(description = "False when paused or stopped", example = "true") @NotNull Boolean isPlaying
) {}

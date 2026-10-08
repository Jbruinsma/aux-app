package com.aux_app.dto.playlist;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Music pieces to add to a playlist")
public record PlaylistMusicPieceAddition(
        @Schema(description = "Public ids of the music pieces to add, 1 to 100", example = "[\"m_92kd0Z\", \"m_3fK81a\"]")
        @NotEmpty @Size(max = 100) List<String> musicPieceIds
) {}

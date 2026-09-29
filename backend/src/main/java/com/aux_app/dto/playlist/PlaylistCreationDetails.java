package com.aux_app.dto.playlist;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

public record PlaylistCreationDetails(
        @Schema(example = "Chill Beats") @NotBlank @Size(max = 36) String playlistName,
        @Schema(example = "true") boolean isPublic,
        @NotNull MultipartFile playlistCover
) {}

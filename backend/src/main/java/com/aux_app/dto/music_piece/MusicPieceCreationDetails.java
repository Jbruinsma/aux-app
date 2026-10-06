package com.aux_app.dto.music_piece;

import com.aux_app.services.ProfanityFilter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

public record MusicPieceCreationDetails(
        @Schema(example = "Dark Fantasy") @NotBlank @Size(max = 200) String name,
        @Schema(example = "a_8f3k2Q") @NotBlank String artistId,
        @Schema(example = "true") boolean isPublic,
        @NotNull MultipartFile coverImage,
        @NotNull MultipartFile mp3File
) {
    public MusicPieceCreationDetails {
        name = ProfanityFilter.mask(name);
    }
}

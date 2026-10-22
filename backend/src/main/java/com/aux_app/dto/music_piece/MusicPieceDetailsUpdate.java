package com.aux_app.dto.music_piece;

import com.aux_app.services.ProfanityFilter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

// Partial update: a null field means "leave unchanged"
@Schema(description = "Multipart form for editing a music piece; a missing field leaves that value unchanged")
public record MusicPieceDetailsUpdate(
        @Schema(description = "New title, at most 200 chars. Profanity is masked. Omit or leave blank to keep the current title", example = "Dark Fantasy")
        @Size(max = 200)
        String name,

        @Schema(description = "Public id of an existing artist. Omit or leave blank to keep the current artist", example = "a_8f3k2Q")
        String artistId,

        // Spring binds a blank value to null
        @Schema(description = "New visibility; omit to keep the current value", example = "true")
        Boolean isPublic,

        // Empty, size and format are checked by UploadService
        @Schema(description = "New cover: JPEG or PNG, at most 5MB and 25 megapixels, at least 256x256. Omit to keep the current cover", type = "string", format = "binary")
        MultipartFile coverImage
) {
    // Runs before validation, so a blank name counts as unchanged instead of failing @Size
    public MusicPieceDetailsUpdate {
        name = name == null || name.isBlank() ? null : ProfanityFilter.mask(name.strip());
        artistId = artistId == null || artistId.isBlank() ? null : artistId;
        coverImage = coverImage == null || coverImage.isEmpty() ? null : coverImage;
    }
}

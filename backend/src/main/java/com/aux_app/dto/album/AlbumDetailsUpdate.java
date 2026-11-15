package com.aux_app.dto.album;

import com.aux_app.services.ProfanityFilter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

// Partial update: a null field means "leave unchanged"
@Schema(description = "Multipart form for editing an album; a missing field leaves that value unchanged")
public record AlbumDetailsUpdate(
        @Schema(description = "New name, at most 100 chars. Profanity is masked. Omit or leave blank to keep the current name", example = "Currents")
        @Size(max = 100)
        String albumName,

        @Schema(description = "New cover: JPEG or PNG, at most 5MB and 25 megapixels, at least 256x256. Omit to keep the current cover", type = "string", format = "binary")
        MultipartFile coverImage
) {
    // Runs before validation, so a blank name counts as unchanged instead of failing @Size
    public AlbumDetailsUpdate {
        albumName = albumName == null || albumName.isBlank() ? null : ProfanityFilter.mask(albumName.strip());
        coverImage = coverImage == null || coverImage.isEmpty() ? null : coverImage;
    }
}

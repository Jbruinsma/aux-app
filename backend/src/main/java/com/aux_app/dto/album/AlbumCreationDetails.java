package com.aux_app.dto.album;

import com.aux_app.services.ProfanityFilter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

@Schema(description = "Multipart form for finding or creating an album")
public record AlbumCreationDetails(
        @Schema(description = "Album name, 1-100 chars, trimmed. Profanity is masked", example = "Currents") @NotBlank @Size(max = 100) String albumName,
        // Checked by AlbumService: only needed when no album has this name yet
        @Schema(description = "Cover: JPEG or PNG, at most 5MB and 25 megapixels, at least 256x256. Send the original; the server crops and resizes. Required when the album is created; ignored when it already exists", type = "string", format = "binary") MultipartFile coverImage
) {
    public AlbumCreationDetails {
        albumName = albumName == null ? null : ProfanityFilter.mask(albumName.strip());
        coverImage = coverImage == null || coverImage.isEmpty() ? null : coverImage;
    }
}

package com.aux_app.dto.playlist;

import com.aux_app.services.ProfanityFilter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

// Partial update: a null field means "leave unchanged"
public record PlaylistDetailsUpdate(
        @Schema(description = "New name, at most 36 chars. Omit or leave blank to keep the current name", example = "My Playlist")
        @Size(max = 36)
        String playlistName,

        // Spring binds a blank value to null
        @Schema(description = "New visibility. Owner only; omit to keep the current value", example = "true")
        Boolean isPublic,

        // Empty, size and format are checked by UploadService
        @Schema(description = "New cover: JPEG or PNG, at most 5MB and 25 megapixels, at least 256x256. Omit to keep the current cover", type = "string", format = "binary")
        MultipartFile playlistCover
) {
    // Runs before validation, so a blank name counts as unchanged instead of failing @Size
    public PlaylistDetailsUpdate {
        playlistName = playlistName == null || playlistName.isBlank() ? null : ProfanityFilter.mask(playlistName.strip());
        playlistCover = playlistCover == null || playlistCover.isEmpty() ? null : playlistCover;
    }
}

package com.aux_app.dto.playlist;

import com.aux_app.services.ProfanityFilter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

// Partial update: a null field means "leave unchanged"
public record PlaylistDetailsUpdate(
        @Schema(example = "My Playlist")
        @Size(max = 36)
        String playlistName,

        // Spring binds a blank value to null
        @Schema(example = "true")
        Boolean isPublic,

        // Empty, size and format are checked by UploadService
        MultipartFile playlistCover
) {
    // Runs before validation, so a blank name counts as unchanged instead of failing @Size
    public PlaylistDetailsUpdate {
        playlistName = playlistName == null || playlistName.isBlank() ? null : ProfanityFilter.mask(playlistName.strip());
        playlistCover = playlistCover == null || playlistCover.isEmpty() ? null : playlistCover;
    }
}

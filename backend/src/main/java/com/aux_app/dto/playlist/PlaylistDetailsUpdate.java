package com.aux_app.dto.playlist;

import com.aux_app.services.ProfanityFilter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

// Partial update: a null field means "leave unchanged"
@Schema(description = "Multipart form for editing a playlist; a missing field leaves that value unchanged")
public record PlaylistDetailsUpdate(
        @Schema(description = "New name, at most 36 chars. Omit or leave blank to keep the current name", example = "My Playlist")
        @Size(max = 36)
        String playlistName,

        // Spring binds a blank value to null
        @Schema(description = "New visibility. Owner only; omit to keep the current value", example = "true")
        Boolean isPublic,

        // Empty, size and format are checked by UploadService
        @Schema(description = "New cover: JPEG or PNG, at most 5MB and 25 megapixels, at least 256x256. Omit to keep the current cover", type = "string", format = "binary")
        MultipartFile playlistCover,

        // Owner only. Normalized by TagService.normalize, which keeps null as "unchanged"
        @Schema(description = "Owner only. Replaces all tags, one value per tag (repeat the field); same rules as on create. Omit to keep the current tags; send a single blank value to remove them all", example = "[\"chill\", \"late night\"]")
        List<String> tags
) {
    // Runs before validation, so a blank name counts as unchanged instead of failing @Size
    public PlaylistDetailsUpdate {
        playlistName = playlistName == null || playlistName.isBlank() ? null : ProfanityFilter.mask(playlistName.strip());
        playlistCover = playlistCover == null || playlistCover.isEmpty() ? null : playlistCover;
    }
}

package com.aux_app.dto.music_piece;

import com.aux_app.services.ProfanityFilter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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

        @Schema(description = "Public id of an album of the piece's artist (after any `artistId` change) to move the piece into. Omit or leave blank to keep the current album", example = "l_4Hq9xT")
        String albumId,

        @Schema(description = "`true` makes the piece a single; it keeps the cover it shows. Can't be sent with `albumId`", example = "false")
        Boolean removeFromAlbum,

        @Schema(description = "`true` shows the album's cover and follows it from now on; the piece's own cover is deleted. `false` keeps the cover the piece shows now as its own. Omit to keep the current choice. `true` can't be sent with `coverImage`", example = "true")
        Boolean useAlbumCover,

        // Empty, size and format are checked by UploadService
        @Schema(description = "New cover: JPEG or PNG, at most 5MB and 25 megapixels, at least 256x256. Becomes the piece's own cover, so it stops using its album's. Omit to keep the current cover", type = "string", format = "binary")
        MultipartFile coverImage,

        // Normalized by TagService.normalize, which keeps null as "unchanged"
        @Schema(description = "Replaces all tags, one value per tag (repeat the field); same rules as on create. Omit to keep the current tags; send a single blank value to remove them all", example = "[\"chill\", \"lo-fi\"]")
        List<String> tags
) {
    // Runs before validation, so a blank name counts as unchanged instead of failing @Size
    public MusicPieceDetailsUpdate {
        name = name == null || name.isBlank() ? null : ProfanityFilter.mask(name.strip());
        artistId = artistId == null || artistId.isBlank() ? null : artistId;
        albumId = albumId == null || albumId.isBlank() ? null : albumId;
        coverImage = coverImage == null || coverImage.isEmpty() ? null : coverImage;
    }
}

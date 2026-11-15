package com.aux_app.dto.music_piece;

import com.aux_app.services.ProfanityFilter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Schema(description = "Multipart form for uploading a music piece")
public record MusicPieceCreationDetails(
        @Schema(description = "Title, 1-200 chars, trimmed. Profanity is masked", example = "Dark Fantasy") @NotBlank @Size(max = 200) String name,
        @Schema(description = "Public id of an existing artist", example = "a_8f3k2Q") @NotBlank String artistId,
        @Schema(description = "Whether anyone can play the piece; defaults to false. A private piece can only be played by its uploader, and by collaborators on private playlists the uploader adds it to", example = "true") boolean isPublic,
        @Schema(description = "Public id of one of the artist's albums; omit for a single", example = "l_4Hq9xT") String albumId,
        // Required without an album; checked in MusicPieceController
        @Schema(description = "Cover: JPEG or PNG, at most 5MB and 25 megapixels, at least 256x256. Send the original; the server crops and resizes. Required for a single; with `albumId`, omit it to use the album's cover", type = "string", format = "binary") MultipartFile coverImage,
        // Exactly one of mp3File and importId; checked in MusicPieceController
        @Schema(description = "MP3, at most 25MB and 30 seconds to 10 minutes long. Counts toward the uploader's 2GB quota. Send this or `importId`", type = "string", format = "binary") MultipartFile mp3File,
        @Schema(description = "Id of a READY YouTube import (`POST /api/imports/youtube`), in place of `mp3File`. Testers and admins only", example = "6f1c2a9e-3b7d-4e0a-9c51-8d2f4b6a7e10") String importId,
        @Schema(description = "Up to 5 tags, one value per tag (repeat the field). Lowercased and trimmed; each is 2-24 letters, digits, spaces, '&' or '-'. Unknown tags are created. Omit for none", example = "[\"chill\", \"lo-fi\"]") List<String> tags
) {
    public MusicPieceCreationDetails {
        name = ProfanityFilter.mask(name);
        albumId = albumId == null || albumId.isBlank() ? null : albumId;
        coverImage = coverImage == null || coverImage.isEmpty() ? null : coverImage;
        mp3File = mp3File == null || mp3File.isEmpty() ? null : mp3File;
        importId = importId == null || importId.isBlank() ? null : importId.strip();
    }
}

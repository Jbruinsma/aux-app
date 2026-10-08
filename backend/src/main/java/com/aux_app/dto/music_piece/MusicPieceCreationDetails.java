package com.aux_app.dto.music_piece;

import com.aux_app.services.ProfanityFilter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

@Schema(description = "Multipart form for uploading a music piece")
public record MusicPieceCreationDetails(
        @Schema(description = "Title, 1-200 chars, trimmed. Profanity is masked", example = "Dark Fantasy") @NotBlank @Size(max = 200) String name,
        @Schema(description = "Public id of an existing artist", example = "a_8f3k2Q") @NotBlank String artistId,
        @Schema(description = "Whether anyone can play the piece; defaults to false. A private piece can only be played by its uploader", example = "true") boolean isPublic,
        @Schema(description = "Cover: JPEG or PNG, at most 5MB and 25 megapixels, at least 256x256. Send the original; the server crops and resizes", type = "string", format = "binary") @NotNull MultipartFile coverImage,
        @Schema(description = "MP3, at most 25MB and 5 seconds to 10 minutes long. Counts toward the uploader's 2GB quota", type = "string", format = "binary") @NotNull MultipartFile mp3File
) {
    public MusicPieceCreationDetails {
        name = ProfanityFilter.mask(name);
    }
}

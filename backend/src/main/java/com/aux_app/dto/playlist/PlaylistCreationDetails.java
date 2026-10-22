package com.aux_app.dto.playlist;

import com.aux_app.services.ProfanityFilter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Schema(description = "Multipart form for creating a playlist")
public record PlaylistCreationDetails(
        @Schema(description = "Name, 1-36 chars", example = "Chill Beats") @NotBlank @Size(max = 36) String playlistName,
        @Schema(description = "Whether anyone can open the playlist; defaults to false", example = "true") boolean isPublic,
        @Schema(description = "Cover: JPEG or PNG, at most 5MB and 25 megapixels, at least 256x256. Send the original; the server crops and resizes", type = "string", format = "binary") @NotNull MultipartFile playlistCover,
        @Schema(description = "Up to 5 tags, one value per tag (repeat the field). Lowercased and trimmed; each is 2-24 letters, digits, spaces, '&' or '-'. Unknown tags are created. Omit for none", example = "[\"chill\", \"late night\"]") List<String> tags
) {
    public PlaylistCreationDetails {
        playlistName = ProfanityFilter.mask(playlistName);
    }
}

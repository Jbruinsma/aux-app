package com.aux_app.dto.artist;

import com.aux_app.services.ProfanityFilter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ArtistCreationDetails(
        @Schema(example = "Tame Impala") @NotBlank @Size(max = 100) String artistName
) {
    public ArtistCreationDetails {
        artistName = ProfanityFilter.mask(artistName);
    }
}

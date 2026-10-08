package com.aux_app.dto.search;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Search parameters, sent as query params")
public record SearchQuery(
        @Schema(description = "What to search: `USERS`, `MUSIC` or `PLAYLISTS`. Required") SearchCategory category,
        @Schema(description = "Text to match anywhere in the name, case-insensitive, 1-200 chars. `MUSIC` also matches the artist name. Required") @Size(min = 1, max= 200) String query,
        @Schema(description = "Page size, 1 to 50; defaults to 10", example = "10") Integer limit,
        @Schema(description = "How many results to skip; defaults to 0", example = "0") Integer offset
) {
}

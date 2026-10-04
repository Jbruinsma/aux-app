package com.aux_app.dto.search;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record SearchQuery(
        SearchCategory category,
        @Schema @Size(min = 1, max= 200) String query,
        @Schema(example = "10") Integer limit,
        @Schema(example = "0") Integer offset
) {
}

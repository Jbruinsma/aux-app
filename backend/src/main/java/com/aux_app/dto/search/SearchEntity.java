package com.aux_app.dto.search;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record SearchEntity<T>(
        @Schema(example = "10") Integer totalResults,
        List<T> results
) {
}

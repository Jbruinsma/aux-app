package com.aux_app.dto.users;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Listeners who share the caller's taste, best match first")
public record MatchesResponse(
        @Schema(description = "Up to 20. Empty when the caller has no plays or favorites yet") List<CompatibilityMatch> matches
) {}

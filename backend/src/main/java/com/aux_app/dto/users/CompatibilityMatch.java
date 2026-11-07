package com.aux_app.dto.users;

import com.aux_app.dto.artist.ArtistSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "A listener who shares the caller's taste")
public record CompatibilityMatch(
        @Schema(description = "The matched user") PlaylistOwner user,
        @Schema(description = "0-100. Levels: 0-19 Low, 20-39 Medium, 40-59 High, 60-79 Very high, 80-100 Super", example = "87") int compatibility,
        @Schema(description = "Up to 3 artists both listen to, the one both play most first. Empty for a match on tags alone") List<ArtistSummary> sharedArtists,
        @Schema(description = "Up to 3 curated tags both listen to, strongest first", example = "[\"r&b\", \"chill\"]") List<String> sharedTags
) {}

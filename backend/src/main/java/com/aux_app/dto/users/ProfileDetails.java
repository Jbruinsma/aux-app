package com.aux_app.dto.users;

import com.aux_app.dto.base.Country;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A user's optional profile fields")
public record ProfileDetails(
   @Schema(description = "Display name; null until set", example = "Joe Mama") String displayName,
   @Schema(description = "Country; null until set", example = "US") Country country,
   @Schema(description = "http(s) link; null until set", example = "https://google.com") String website,
   @Schema(description = "Short bio, at most 200 chars; null until set", example = "I am a cool guy") String about
) {}

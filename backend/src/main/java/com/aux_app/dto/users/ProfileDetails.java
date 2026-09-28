package com.aux_app.dto.users;

import com.aux_app.dto.base.Country;
import io.swagger.v3.oas.annotations.media.Schema;

public record ProfileDetails(
   @Schema(example = "Joe Mama") String displayName,
   @Schema(example = "US") Country country,
   @Schema(example = "https://google.com") String website,
   @Schema(example = "I am a cool guy") String about
) {}

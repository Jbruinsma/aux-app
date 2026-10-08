package com.aux_app.dto.users;

import com.aux_app.dto.base.Country;
import com.aux_app.services.ProfanityFilter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

@Schema(description = "Full replacement of a user's profile fields")
public record ProfileDetailsUpdate(
        @Schema(description = "Display name, 3-15 chars. Missing or blank clears it. Profanity is masked", example = "Joe Mama")
        @Size(min = 3, max = 15)
        String displayName,

        @Schema(description = "Country code. Missing clears it", example = "US")
        Country country,

        // http(s) only: blocks javascript:/data:/ftp:/file: since the frontend renders this as a link
        @Schema(description = "http(s) URL without spaces, at most 2048 chars. Missing or blank clears it", example = "https://google.com")
        @Size(max = 2048)
        @Pattern(regexp = "https?://\\S+", flags = Pattern.Flag.CASE_INSENSITIVE, message = "must be an http(s) URL without spaces")
        @URL
        String website,

        @Schema(description = "Short bio, at most 200 chars. Missing or blank clears it. Profanity is masked", example = "I am a cool guy")
        @Size(max = 200)
        String about
) {
    public ProfileDetailsUpdate {
        displayName = ProfanityFilter.mask(blankToNull(displayName));
        website = blankToNull(website);
        about = ProfanityFilter.mask(blankToNull(about));
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.strip();
        return t.isEmpty() ? null : t;
    }
}

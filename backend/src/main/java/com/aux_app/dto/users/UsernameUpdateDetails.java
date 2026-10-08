package com.aux_app.dto.users;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The username to switch to")
public record UsernameUpdateDetails(
        @Schema(description = "3-16 letters, numbers or underscores. Kept as typed, but unique ignoring case", example = "new_name") String username
) {
}

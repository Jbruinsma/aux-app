package com.aux_app.dto.users;

import io.swagger.v3.oas.annotations.media.Schema;

public record UsernameUpdateDetails(
        @Schema(example = "new_name") String username
) {
}

package com.aux_app.dto.users;

import io.swagger.v3.oas.annotations.media.Schema;

public record UserExistance(
        @Schema(example = "true") boolean exists
) {}

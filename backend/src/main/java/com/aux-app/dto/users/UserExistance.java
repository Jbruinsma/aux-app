package com.aux.dto.users;

import io.swagger.v3.oas.annotations.media.Schema;

public record UserExistance(
        @Schema(example = "true") boolean exists
) {}

package com.aux_app.dto.users;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Whether a username is taken")
public record UserExistance(
        @Schema(description = "True when the username is taken or reserved", example = "true") boolean exists
) {}

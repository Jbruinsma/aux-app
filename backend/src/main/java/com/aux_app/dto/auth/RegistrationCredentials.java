package com.aux_app.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistrationCredentials(
        @Schema(example = "justin") @NotBlank @Size(min= 3, max= 16) String username,
        @Schema(example = "example@gmail.com") @NotBlank @Size(min= 3, max= 254) String email,
        @Schema(example = "correct-horse-battery") @NotBlank @Size(min= 8, max= 32) String password
) {}

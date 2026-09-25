package com.aux.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginCredentials(
        @Schema(example = "justin") @NotBlank @Size(min= 3, max= 16) String username,
        @Schema(example = "correct-horse-battery") @NotBlank @Size(min= 8, max= 32) String password
) {}

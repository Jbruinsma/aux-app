package com.aux_app.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordChange(
        @Schema(example = "123456") @NotBlank @Pattern(regexp = "\\d{6}") String code,
        @Schema(example = "correct-horse-battery") @NotBlank @Size(min= 8, max= 32) String newPassword
) {}

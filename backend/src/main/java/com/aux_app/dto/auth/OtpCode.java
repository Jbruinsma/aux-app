package com.aux_app.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OtpCode(
        @Schema(example = "123456") @NotBlank @Pattern(regexp = "\\d{6}") String code
) {}

package com.aux_app.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "A 6-digit one-time code from an email")
public record OtpCode(
        @Schema(description = "6-digit code from the email", example = "123456") @NotBlank @Pattern(regexp = "\\d{6}") String code
) {}

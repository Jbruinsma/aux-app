package com.aux_app.dto.base;

public record ErrorDetails(
        String error,
        String code,
        String message,
        String parameter
) {}

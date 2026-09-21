package com.aux.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistrationCredentials(
        @NotBlank @Size(min= 3, max= 16) String username,
        @NotBlank @Size(min= 8, max= 32) String password,
        @NotBlank @Size(min= 3, max= 320) String email
) {}

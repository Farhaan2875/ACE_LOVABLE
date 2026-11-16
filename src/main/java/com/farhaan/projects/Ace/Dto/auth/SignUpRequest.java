package com.farhaan.projects.Ace.Dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignUpRequest(
        @Email @NotBlank String username,
        @NotBlank @Size(min = 1, max = 30) String name,
        @NotBlank @Size(min = 8, max = 72) String password   // 72 = BCrypt's limit
) {
}

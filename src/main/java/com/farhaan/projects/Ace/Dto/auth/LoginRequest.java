package com.farhaan.projects.Ace.Dto.auth;

import jakarta.validation.constraints.*;

public record LoginRequest(
        @Email @NotBlank String username,
        @NotBlank @Size(max = 72) String password
) {
}

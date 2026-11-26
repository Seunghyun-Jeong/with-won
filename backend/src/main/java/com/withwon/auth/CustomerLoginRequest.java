package com.withwon.auth;

import jakarta.validation.constraints.NotBlank;

public record CustomerLoginRequest(
        @NotBlank String loginId,
        @NotBlank String password
) {
}

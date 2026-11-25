package com.withwon.auth;

import jakarta.validation.constraints.NotBlank;

public record EmployeeLoginRequest(
        @NotBlank String loginId,
        @NotBlank String password
) {
}

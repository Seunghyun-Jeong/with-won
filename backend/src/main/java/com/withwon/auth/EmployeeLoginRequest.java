package com.withwon.auth;

import jakarta.validation.constraints.NotBlank;

public record EmployeeLoginRequest(
        @NotBlank String employeeNumber,
        @NotBlank String password
) {
}

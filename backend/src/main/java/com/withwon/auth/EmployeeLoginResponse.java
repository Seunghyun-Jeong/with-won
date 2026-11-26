package com.withwon.auth;

public record EmployeeLoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        Long userId,
        String role
) {
}

package com.withwon.auth;

public record CustomerLoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        Long userId,
        String role
) {
}

package com.vspicy.auth.dto;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        CurrentUserResponse user
) {
}

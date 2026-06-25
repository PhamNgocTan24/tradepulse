package com.tradepulse.auth.dto.response;

import java.util.UUID;

public record AuthResponse(
        UUID userId,
        String accessToken,
        String refreshToken,
        long accessTokenExpiresIn,   // seconds
        String tokenType             // always "Bearer"
) {
    public static AuthResponse of(UUID userId, String accessToken,
                                   String refreshToken, long expiresIn) {
        return new AuthResponse(userId, accessToken, refreshToken, expiresIn, "Bearer");
    }
}

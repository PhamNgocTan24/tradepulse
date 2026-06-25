package com.tradepulse.auth.service;

import com.tradepulse.auth.dto.request.LoginRequest;
import com.tradepulse.auth.dto.request.RefreshTokenRequest;
import com.tradepulse.auth.dto.request.RegisterRequest;
import com.tradepulse.auth.dto.response.AuthResponse;
import com.tradepulse.auth.dto.response.TotpSetupResponse;

import java.util.UUID;

public interface AuthService {

    /** Registers a new user; publishes USER_REGISTERED to Kafka. */
    AuthResponse register(RegisterRequest request);

    /** Authenticates via email+password (+ TOTP if enabled). */
    AuthResponse login(LoginRequest request);

    /** Rotates the refresh token and issues a new access token. */
    AuthResponse refresh(RefreshTokenRequest request);

    /** Invalidates the access token by adding its jti to the Redis blacklist. */
    void logout(String accessToken);

    /** Generates a new TOTP secret and returns the QR code for the user to scan. */
    TotpSetupResponse setupTotp(UUID userId);

    /** Verifies the provided TOTP code and enables 2FA on the account if valid. */
    void confirmTotp(UUID userId, String totpCode);
}

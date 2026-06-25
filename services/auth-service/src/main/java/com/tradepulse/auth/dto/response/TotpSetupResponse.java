package com.tradepulse.auth.dto.response;

public record TotpSetupResponse(
        String otpAuthUri,   // otpauth://totp/... — used by authenticator apps
        String qrCodeBase64  // Base64-encoded PNG QR code image
) {}

package com.tradepulse.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password,
        /** Optional — required when TOTP 2FA is enabled for the account. */
        String totpCode
) {}

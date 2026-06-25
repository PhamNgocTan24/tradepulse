package com.tradepulse.security.jwt;

import java.util.List;
import java.util.UUID;

/**
 * Parsed claims extracted from a validated JWT.
 * Produced by JwtUtils after token verification.
 */
public record JwtClaims(
        UUID userId,
        String email,
        String jti,
        List<String> roles
) {}

package com.tradepulse.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT configuration bound from application.yml (jwt.* prefix).
 * Key material is fetched from AWS Secrets Manager via the secret name.
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtConfig(
        String privateKeySecret,
        String publicKeySecret,
        int accessTokenExpiryMinutes,
        int refreshTokenExpiryDays
) {}

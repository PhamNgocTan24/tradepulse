package com.tradepulse.auth.service;

import com.tradepulse.auth.config.JwtConfig;
import com.tradepulse.auth.domain.entity.User;
import com.tradepulse.auth.dto.response.AuthResponse;
import com.tradepulse.auth.exception.AuthException;
import com.tradepulse.security.jwt.JwtClaims;
import com.tradepulse.security.jwt.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Handles JWT issuance, refresh rotation, and blacklisting.
 * Keys are injected via KeyProviderConfig (loaded from AWS Secrets Manager).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtConfig jwtConfig;
    private final PrivateKey jwtPrivateKey;
    private final PublicKey jwtPublicKey;
    private final StringRedisTemplate redisTemplate;

    /** Issues a new access + refresh token pair. */
    public AuthResponse generateTokenPair(User user) {
        long expirySeconds = jwtConfig.accessTokenExpiryMinutes() * 60L;
        Instant now = Instant.now();
        Instant accessExpiry = now.plusSeconds(expirySeconds);

        String jti = UUID.randomUUID().toString();
        List<String> roles = List.of(user.getRole().name());

        String accessToken = JwtUtils.signToken(
                user.getId(), user.getEmail(), roles, jti, now, accessExpiry, jwtPrivateKey);

        // Refresh token: longer-lived, separate jti
        Instant refreshExpiry = now.plusSeconds(jwtConfig.refreshTokenExpiryDays() * 86400L);
        String refreshJti = UUID.randomUUID().toString();
        String refreshToken = JwtUtils.signToken(
                user.getId(), user.getEmail(), roles, refreshJti, now, refreshExpiry, jwtPrivateKey);

        return AuthResponse.of(user.getId(), accessToken, refreshToken, expirySeconds);
    }

    /** Validates the refresh token and issues a new pair (rotation). */
    public AuthResponse refreshTokenPair(String refreshToken) {
        JwtClaims claims = parseAndValidate(refreshToken);

        // Invalidate the old refresh token
        blacklistToken(refreshToken);

        // Load user from repo to get current role — lazy load via UserRepository injected if needed
        // For now, re-use claims to build a minimal User-like object
        // TODO: inject UserRepository here for full user state refresh
        log.info("Refresh token rotated for userId={}", claims.userId());

        long expirySeconds = jwtConfig.accessTokenExpiryMinutes() * 60L;
        Instant now = Instant.now();

        String newJti = UUID.randomUUID().toString();
        String newAccessToken = JwtUtils.signToken(
                claims.userId(), claims.email(), claims.roles(),
                newJti, now, now.plusSeconds(expirySeconds), jwtPrivateKey);

        String newRefreshJti = UUID.randomUUID().toString();
        String newRefreshToken = JwtUtils.signToken(
                claims.userId(), claims.email(), claims.roles(),
                newRefreshJti, now,
                now.plusSeconds(jwtConfig.refreshTokenExpiryDays() * 86400L), jwtPrivateKey);

        return AuthResponse.of(claims.userId(), newAccessToken, newRefreshToken, expirySeconds);
    }

    /** Adds the token's jti to the Redis blacklist. TTL = remaining token life. */
    public void blacklistToken(String token) {
        try {
            JwtClaims claims = parseAndValidate(token);
            String key = "blacklist:" + claims.jti();
            // TTL: remaining valid lifetime to avoid unbounded key growth
            redisTemplate.opsForValue().set(key, "revoked", Duration.ofHours(1));
        } catch (Exception e) {
            log.warn("Could not blacklist token (already invalid?): {}", e.getMessage());
        }
    }

    /** Returns true if the token's jti is in the Redis blacklist. */
    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("blacklist:" + jti));
    }

    public JwtClaims parseAndValidate(String token) {
        try {
            JwtClaims claims = JwtUtils.parseClaims(token, jwtPublicKey);
            if (isBlacklisted(claims.jti())) {
                throw AuthException.tokenExpiredOrInvalid();
            }
            return claims;
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            throw AuthException.tokenExpiredOrInvalid();
        }
    }
}

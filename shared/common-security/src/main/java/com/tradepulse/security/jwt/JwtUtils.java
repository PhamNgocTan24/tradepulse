package com.tradepulse.security.jwt;

import com.tradepulse.security.constants.SecurityConstants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Stateless JWT utility — sign, build, and parse RS256 tokens.
 * <p>
 * Auth-service uses signToken() to issue tokens.
 * All other services use parseClaims() to validate incoming tokens.
 * Key material is loaded from AWS Secrets Manager by each service's KeyConfig.
 */
public final class JwtUtils {

    private static final Logger log = LoggerFactory.getLogger(JwtUtils.class);

    private JwtUtils() {}

    /**
     * Builds and signs an RS256 JWT access token.
     *
     * @param userId      subject (UUID)
     * @param email       user email added as a claim
     * @param roles       list of role strings e.g. ["USER", "TRADER"]
     * @param jti         unique token identifier (used for blacklisting on logout)
     * @param issuedAt    token issue time
     * @param expiresAt   token expiry time
     * @param privateKey  RS256 private key from AWS Secrets Manager
     * @return compact JWT string
     */
    public static String signToken(UUID userId, String email, List<String> roles,
                                   String jti, Instant issuedAt, Instant expiresAt,
                                   PrivateKey privateKey) {
        return Jwts.builder()
                .header()
                    .keyId("tradepulse-jwt-key")
                    .and()
                .subject(userId.toString())
                .id(jti)
                .claim(SecurityConstants.CLAIM_EMAIL, email)
                .claim(SecurityConstants.CLAIM_ROLES, roles)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(privateKey)
                .compact();
    }

    /**
     * Parses and validates an RS256 JWT, returning strongly-typed claims.
     *
     * @param token     compact JWT string (without "Bearer " prefix)
     * @param publicKey RS256 public key for signature verification
     * @return parsed JwtClaims
     * @throws JwtException if the token is invalid, expired, or tampered
     */
    @SuppressWarnings("unchecked")
    public static JwtClaims parseClaims(String token, PublicKey publicKey) {
        Claims claims = Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        UUID userId = UUID.fromString(claims.getSubject());
        String email = claims.get(SecurityConstants.CLAIM_EMAIL, String.class);
        String jti = claims.getId();
        List<String> roles = (List<String>) claims.get(SecurityConstants.CLAIM_ROLES);

        return new JwtClaims(userId, email, jti, roles != null ? roles : List.of());
    }

    /**
     * Extracts the "Bearer " prefix from an Authorization header value.
     * Returns null if the header is missing or malformed.
     */
    public static String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null
                || !authorizationHeader.startsWith(SecurityConstants.BEARER_PREFIX)) {
            return null;
        }
        return authorizationHeader.substring(SecurityConstants.BEARER_PREFIX.length()).trim();
    }
}

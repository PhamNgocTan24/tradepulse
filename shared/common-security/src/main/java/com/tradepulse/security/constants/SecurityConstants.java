package com.tradepulse.security.constants;

/**
 * Shared security constants used across services.
 * Keep service-specific role names in each service's own config.
 */
public final class SecurityConstants {

    private SecurityConstants() {}

    // HTTP header names
    public static final String AUTH_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    // JWT claim names
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_USER_ID = "sub";
    public static final String CLAIM_JTI = "jti";
    public static final String CLAIM_EMAIL = "email";

    // Token types
    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    // Redis key patterns (shared across services)
    public static final String REDIS_KEY_BLACKLIST = "blacklist:";
    public static final String REDIS_KEY_RATE_LIMIT = "rate_limit:";

    // Role names
    public static final String ROLE_USER = "USER";
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_TRADER = "TRADER";

    // Actuator paths that skip JWT validation
    public static final String ACTUATOR_PATH = "/actuator/**";
}

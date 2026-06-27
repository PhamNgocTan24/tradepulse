package com.tradepulse.gateway.config;

import com.tradepulse.security.constants.SecurityConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;

import java.util.Arrays;

/**
 * WebFlux Security Configuration for the API Gateway.
 * Configured using a Dual Security Filter Chain approach to handle public and protected routes differently.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /**
     * Paths that are fully public — no JWT validation is performed on these paths.
     * Any token supplied in the Authorization header (e.g. stale/expired tokens from Swagger UI)
     * is silently bypassed and does not trigger 401 Unauthorized errors.
     */
    private static final String[] PUBLIC_PATHS = {
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/.well-known/jwks.json",
            "/api/auth/oauth2/**",
            "/api/users/leaderboard",
            SecurityConstants.ACTUATOR_PATH,
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/webjars/**",
            "/v3/api-docs/**",
            "/api/*/v3/api-docs",
            "/ws/**"
    };

    /**
     * Chain 1 (Highest priority - Order 1): Public endpoints.
     * Match requests starting with any pattern in PUBLIC_PATHS.
     * Because this chain does NOT define an oauth2ResourceServer, the Bearer token validation
     * filter is completely bypassed.
     */
    @Bean
    @Order(1)
    public SecurityWebFilterChain publicSecurityFilterChain(ServerHttpSecurity http) {
        // Combine all public path pattern matchers into a single OrServerWebExchangeMatcher
        ServerWebExchangeMatcher publicMatcher = new OrServerWebExchangeMatcher(
                Arrays.stream(PUBLIC_PATHS)
                        .map(PathPatternParserServerWebExchangeMatcher::new)
                        .toArray(ServerWebExchangeMatcher[]::new)
        );

        return http
                // Restrict this filter chain strictly to the matched public endpoints
                .securityMatcher(publicMatcher)
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                // Permit all exchanges within this scope
                .authorizeExchange(auth -> auth.anyExchange().permitAll())
                .build();
    }

    /**
     * Chain 2 (Fallback - Order 2): Protected endpoints.
     * Matches all other requests not captured by the public matcher in Chain 1.
     * Validates the Bearer JWT token signature using the JWKS endpoint of auth-service.
     */
    @Bean
    @Order(2)
    public SecurityWebFilterChain protectedSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                // Enforce authentication for all other exchanges
                .authorizeExchange(auth -> auth.anyExchange().authenticated())
                // Enable OAuth2 Resource Server for JWT verification (JWKS URI configured in application.yml)
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
                .build();
    }
}

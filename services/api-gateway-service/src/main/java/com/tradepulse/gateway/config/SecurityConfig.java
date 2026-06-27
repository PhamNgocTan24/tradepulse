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

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /**
     * Paths that are fully public — no JWT validation at all.
     * Invalid/expired tokens in the Authorization header are silently ignored.
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
     * Chain 1 (highest priority): Public endpoints — no OAuth2 resource server filter.
     * Even if the client sends a stale/invalid Bearer token, the request goes through.
     */
    @Bean
    @Order(1)
    public SecurityWebFilterChain publicSecurityFilterChain(ServerHttpSecurity http) {
        ServerWebExchangeMatcher publicMatcher = new OrServerWebExchangeMatcher(
                Arrays.stream(PUBLIC_PATHS)
                        .map(PathPatternParserServerWebExchangeMatcher::new)
                        .toArray(ServerWebExchangeMatcher[]::new)
        );

        return http
                .securityMatcher(publicMatcher)
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(auth -> auth.anyExchange().permitAll())
                .build();
    }

    /**
     * Chain 2: All other endpoints — JWT validated via JWKS from auth-service.
     */
    @Bean
    @Order(2)
    public SecurityWebFilterChain protectedSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(auth -> auth.anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
                .build();
    }
}

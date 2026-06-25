package com.tradepulse.gateway.config;

import com.tradepulse.security.constants.SecurityConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(auth -> auth
                        // Public — auth endpoints and actuator
                        .pathMatchers(
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/.well-known/jwks.json",
                                "/api/auth/oauth2/**",
                                "/api/users/leaderboard",
                                SecurityConstants.ACTUATOR_PATH
                        ).permitAll()
                        // WebSocket upgrade paths
                        .pathMatchers("/ws/**").permitAll()
                        .anyExchange().authenticated()
                )
                // JWT validation via JWKS URI from auth-service (configured in application.yml)
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
                .build();
    }
}

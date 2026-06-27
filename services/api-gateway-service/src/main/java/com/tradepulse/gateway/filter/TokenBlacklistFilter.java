package com.tradepulse.gateway.filter;

import com.tradepulse.security.constants.SecurityConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Checks that the JWT jti (token ID) has not been blacklisted in Redis.
 * Runs after Spring Security has validated the JWT signature.
 */
@Slf4j
@Component
public class TokenBlacklistFilter implements GlobalFilter, Ordered {

    private final ReactiveStringRedisTemplate redisTemplate;

    public TokenBlacklistFilter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public int getOrder() {
        return -100; // run right after auth, before rate limiting
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Retrieve the authenticated security principal from the exchange context
        return exchange.getPrincipal()
                // Only inspect requests containing a valid Spring Security JWT authentication token
                .filter(p -> p instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(JwtAuthenticationToken::getToken)
                // If JWT is present, perform the blacklist lookup asynchronously
                .flatMap(jwt -> checkBlacklist(exchange, chain, jwt))
                // Fallback: If no JWT is found (anonymous requests), let the request bypass the check
                .switchIfEmpty(chain.filter(exchange));
    }

    private Mono<Void> checkBlacklist(ServerWebExchange exchange,
                                       GatewayFilterChain chain,
                                       Jwt jwt) {
        String jti = jwt.getId(); // Retrieve the unique JWT ID (jti)
        if (jti == null) return chain.filter(exchange);

        String key = SecurityConstants.REDIS_KEY_BLACKLIST + jti;
        // Non-blocking query to Redis to check if this specific token ID was blacklisted (e.g. logged out)
        return redisTemplate.hasKey(key)
                .flatMap(blacklisted -> {
                    if (Boolean.TRUE.equals(blacklisted)) {
                        log.warn("Blacklisted token blocked: jti={}", jti);
                        // Deny request and set HTTP status 401 Unauthorized
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        // exchange.getResponse().setComplete() terminates the connection immediately without sending it downstream
                        return exchange.getResponse().setComplete();
                    }
                    // Token is not blacklisted, continue through filter chain
                    return chain.filter(exchange);
                });
    }
}

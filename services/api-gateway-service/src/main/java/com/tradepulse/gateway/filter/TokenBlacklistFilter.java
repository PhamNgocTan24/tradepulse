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
        return exchange.getPrincipal()
                .filter(p -> p instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(JwtAuthenticationToken::getToken)
                .flatMap(jwt -> checkBlacklist(exchange, chain, jwt))
                .switchIfEmpty(chain.filter(exchange));
    }

    private Mono<Void> checkBlacklist(ServerWebExchange exchange,
                                       GatewayFilterChain chain,
                                       Jwt jwt) {
        String jti = jwt.getId();
        if (jti == null) return chain.filter(exchange);

        String key = SecurityConstants.REDIS_KEY_BLACKLIST + jti;
        return redisTemplate.hasKey(key)
                .flatMap(blacklisted -> {
                    if (Boolean.TRUE.equals(blacklisted)) {
                        log.warn("Blacklisted token blocked: jti={}", jti);
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        return exchange.getResponse().setComplete();
                    }
                    return chain.filter(exchange);
                });
    }
}

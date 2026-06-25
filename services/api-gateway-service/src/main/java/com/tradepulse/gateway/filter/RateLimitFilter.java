package com.tradepulse.gateway.filter;

import com.tradepulse.security.constants.SecurityConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

import java.time.Duration;

/**
 * Redis token-bucket rate limiter — increments a counter per user per minute.
 * Key: rate_limit:{user_id}   TTL: 60s
 */
@Slf4j
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    @Value("${rate-limit.requests-per-minute:60}")
    private long requestsPerMinute;

    private final ReactiveStringRedisTemplate redisTemplate;

    public RateLimitFilter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public int getOrder() {
        return -90; // run after security, before routing
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .filter(p -> p instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(JwtAuthenticationToken::getToken)
                .map(Jwt::getSubject)
                .flatMap(userId -> checkRateLimit(exchange, chain, userId))
                .switchIfEmpty(chain.filter(exchange)); // anonymous requests pass through
    }

    private Mono<Void> checkRateLimit(ServerWebExchange exchange,
                                       GatewayFilterChain chain,
                                       String userId) {
        String key = SecurityConstants.REDIS_KEY_RATE_LIMIT + userId;

        return redisTemplate.opsForValue()
                .increment(key)
                .flatMap(count -> {
                    if (count == 1) {
                        // First request in window — set TTL
                        return redisTemplate.expire(key, Duration.ofSeconds(60))
                                .thenReturn(count);
                    }
                    return Mono.just(count);
                })
                .flatMap(count -> {
                    if (count > requestsPerMinute) {
                        log.warn("Rate limit exceeded for userId={}, count={}", userId, count);
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        return exchange.getResponse().setComplete();
                    }
                    return chain.filter(exchange);
                });
    }
}

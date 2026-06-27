package com.tradepulse.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetAddress;

/**
 * Access log filter — implemented as a {@link WebFilter} (not {@link org.springframework.cloud.gateway.filter.GlobalFilter})
 * so that it executes BEFORE Spring Security's WebFilterChain.
 *
 * <p>This guarantees that ALL requests — including those rejected with 401/403 — are logged.</p>
 *
 * <pre>
 * [GATEWAY] --> GET  /api/auth/totp/setup  [ip=127.0.0.1]
 * [GATEWAY] <-- GET  /api/auth/totp/setup  401 (3ms)
 * </pre>
 *
 * <p>Order: {@link Ordered#HIGHEST_PRECEDENCE} — runs before everything, including Spring Security.</p>
 */
@Slf4j
@Component
public class LoggingFilter implements WebFilter, Ordered {

    @Override
    public int getOrder() {
        // HIGHEST_PRECEDENCE ensures this runs before Spring Security WebFilterChain
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // --- PHASE 1: Synchronous Pre-processing (Executed immediately on the incoming thread) ---
        // Extract basic HTTP request details and capture the start timestamp.
        ServerHttpRequest request = exchange.getRequest();
        HttpMethod method  = request.getMethod();
        String path        = request.getURI().getRawPath();
        String query       = request.getURI().getRawQuery();
        String ip          = resolveClientIp(request);
        long   start       = System.currentTimeMillis();

        String fullPath = query != null ? path + "?" + query : path;

        // Log the incoming request immediately — before authentication or downstream filters are executed.
        log.info("[GATEWAY] --> {} {}  [ip={}]", method, fullPath, ip);

        // --- PHASE 2: Non-blocking Forwarding ---
        // chain.filter(exchange) initiates the pipeline and returns a Mono<Void> instantly.
        // It does NOT block the execution thread while waiting for the downstream microservices to respond.
        return chain.filter(exchange)
                // --- PHASE 3: Asynchronous Post-processing (Callback execution) ---
                // doFinally registers a callback executed when the downstream execution terminates 
                // (either successfully, with an error, or cancelled).
                .doFinally(signal -> {
                    ServerHttpResponse response = exchange.getResponse();
                    int status = response.getStatusCode() != null
                            ? response.getStatusCode().value()
                            : 0;
                    long elapsed = System.currentTimeMillis() - start;

                    // Log the outbound response duration and HTTP status code.
                    // This callback runs asynchronously once the remote microservice finishes responding.
                    if (status >= 500) {
                        log.error("[GATEWAY] <-- {} {}  {} ({}ms) [ip={}]",
                                method, path, status, elapsed, ip);
                    } else if (status >= 400) {
                        log.warn("[GATEWAY] <-- {} {}  {} ({}ms) [ip={}]",
                                method, path, status, elapsed, ip);
                    } else {
                        log.info("[GATEWAY] <-- {} {}  {} ({}ms) [ip={}]",
                                method, path, status, elapsed, ip);
                    }
                });
    }

    /**
     * Resolve real client IP — respects X-Forwarded-For header set by proxies/load balancers.
     */
    private String resolveClientIp(ServerHttpRequest request) {
        String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For may contain a comma-separated list; first entry is original client
            return forwarded.split(",")[0].trim();
        }
        InetAddress addr = request.getRemoteAddress() != null
                ? request.getRemoteAddress().getAddress()
                : null;
        return addr != null ? addr.getHostAddress() : "unknown";
    }
}


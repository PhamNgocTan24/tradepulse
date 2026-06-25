package com.tradepulse.marketdata.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradepulse.marketdata.domain.entity.MarketTick;
import com.tradepulse.marketdata.service.MarketDataService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Connects to Binance WebSocket combined stream, normalises tickers to MarketTick,
 * and delegates to MarketDataService for fan-out (MongoDB → Redis → Kafka → STOMP).
 *
 * Reconnection: exponential backoff 1s → 2s → 4s → 8s → max 30s.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BinanceWebSocketClient {

    @Value("${binance.websocket.uri:wss://stream.binance.com:9443/stream}")
    private String baseUri;

    @Value("${binance.websocket.streams}")
    private List<String> streams;

    @Value("${binance.websocket.reconnect.initial-delay-ms:1000}")
    private long initialDelayMs;

    @Value("${binance.websocket.reconnect.max-delay-ms:30000}")
    private long maxDelayMs;

    private final MarketDataService marketDataService;
    private final ObjectMapper objectMapper;

    private volatile boolean running = true;

    @PostConstruct
    public void connect() {
        String streamParam = streams.stream().collect(Collectors.joining("/"));
        URI uri = URI.create(baseUri + "?streams=" + streamParam);
        log.info("Connecting to Binance WebSocket: {}", uri);
        startConnection(uri);
    }

    @PreDestroy
    public void disconnect() {
        running = false;
        log.info("BinanceWebSocketClient shutting down");
    }

    private void startConnection(URI uri) {
        ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();
        client.execute(uri, session ->
                session.receive()
                        .map(WebSocketMessage::getPayloadAsText)
                        .doOnNext(this::handleMessage)
                        .then()
        )
        .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofMillis(initialDelayMs))
                .maxBackoff(Duration.ofMillis(maxDelayMs))
                .doBeforeRetry(signal ->
                        log.warn("Binance WS disconnected, retrying (attempt {}): {}",
                                signal.totalRetries() + 1, signal.failure().getMessage()))
        )
        .subscribe(
                v -> log.info("Binance WS session ended cleanly"),
                err -> log.error("Binance WS fatal error", err)
        );
    }

    private void handleMessage(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode data = root.path("data");
            if (data.isMissingNode()) return;

            String eventType = data.path("e").asText();
            if (!"24hrTicker".equals(eventType)) return;

            MarketTick tick = MarketTick.builder()
                    .symbol(data.path("s").asText().toUpperCase())
                    .price(new BigDecimal(data.path("c").asText()))       // close/last price
                    .volume24h(new BigDecimal(data.path("v").asText()))
                    .priceChangePercent24h(new BigDecimal(data.path("P").asText()))
                    .highPrice24h(new BigDecimal(data.path("h").asText()))
                    .lowPrice24h(new BigDecimal(data.path("l").asText()))
                    .timestamp(Instant.now())
                    .source("BINANCE")
                    .build();

            marketDataService.processTick(tick);

        } catch (Exception e) {
            log.warn("Failed to parse Binance message: {}", e.getMessage());
        }
    }
}

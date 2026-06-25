package com.tradepulse.common.dto.kafka.market;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Kafka event payload for the 'market-data' topic.
 * Partitioned by symbol (e.g. BTCUSDT).
 * Producer:  market-data-service
 * Consumers: matching-engine (limit order evaluation), notification-service (price alerts)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MarketDataEvent(
        String symbol,
        BigDecimal price,
        BigDecimal volume24h,
        BigDecimal priceChangePercent24h,
        BigDecimal highPrice24h,
        BigDecimal lowPrice24h,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant timestamp
) {}

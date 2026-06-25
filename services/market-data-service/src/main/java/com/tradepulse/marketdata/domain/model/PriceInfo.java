package com.tradepulse.marketdata.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Read model returned to REST clients — current price snapshot from Redis.
 */
public record PriceInfo(
        String symbol,
        BigDecimal price,
        BigDecimal volume24h,
        BigDecimal priceChangePercent24h,
        BigDecimal highPrice24h,
        BigDecimal lowPrice24h,
        Instant updatedAt
) {}

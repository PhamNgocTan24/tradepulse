package com.tradepulse.matching.engine;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A single resting order in the OrderBook.
 * Price-time priority: for bids, higher price wins; ties broken by earlier timestamp.
 */
public record OrderBookEntry(
        UUID orderId,
        UUID userId,
        String symbol,
        String side,          // "BUY" or "SELL"
        BigDecimal price,
        BigDecimal quantity,
        BigDecimal remainingQuantity,
        Instant timestamp
) {
    public OrderBookEntry withRemaining(BigDecimal remaining) {
        return new OrderBookEntry(orderId, userId, symbol, side, price,
                quantity, remaining, timestamp);
    }
}

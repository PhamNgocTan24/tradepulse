package com.tradepulse.common.dto.kafka.order;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Kafka event payload for the 'order-events' topic.
 * Partitioned by order_id.
 * Producers: order-service (NEW_ORDER, CANCEL_ORDER), matching-engine (FILLED, PARTIAL_FILL, CANCELLED)
 * Consumers: order-service, matching-engine, portfolio-service
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderEvent(
        UUID eventId,
        OrderEventType eventType,
        UUID orderId,
        UUID userId,
        String symbol,
        String side,         // BUY or SELL
        String orderType,    // MARKET or LIMIT
        BigDecimal quantity,
        BigDecimal price,    // null for MARKET orders
        BigDecimal filledQuantity,
        BigDecimal averageFillPrice,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant timestamp
) {
    /** Convenience factory for order-service to publish a new order. */
    public static OrderEvent newOrder(UUID orderId, UUID userId, String symbol,
                                      String side, String orderType,
                                      BigDecimal quantity, BigDecimal price) {
        return new OrderEvent(
                UUID.randomUUID(), OrderEventType.NEW_ORDER,
                orderId, userId, symbol, side, orderType,
                quantity, price, BigDecimal.ZERO, null, Instant.now()
        );
    }
}

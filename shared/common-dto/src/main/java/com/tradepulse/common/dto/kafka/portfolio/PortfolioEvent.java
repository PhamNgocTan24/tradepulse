package com.tradepulse.common.dto.kafka.portfolio;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Kafka event payload for the 'portfolio-events' topic.
 * Partitioned by user_id.
 * Producer:  matching-engine (ORDER_FILLED, PARTIAL_FILL)
 * Consumer:  portfolio-service
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PortfolioEvent(
        UUID eventId,
        PortfolioEventType eventType,
        UUID orderId,
        UUID userId,
        String symbol,
        String side,             // BUY or SELL
        BigDecimal filledQuantity,
        BigDecimal fillPrice,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant timestamp
) {}

package com.tradepulse.order.dto.response;

import com.tradepulse.order.domain.enums.OrderSide;
import com.tradepulse.order.domain.enums.OrderStatus;
import com.tradepulse.order.domain.enums.OrderType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID userId,
        String symbol,
        OrderSide side,
        OrderType orderType,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal filledQuantity,
        BigDecimal averageFillPrice,
        OrderStatus status,
        Instant createdAt,
        Instant updatedAt
) {}

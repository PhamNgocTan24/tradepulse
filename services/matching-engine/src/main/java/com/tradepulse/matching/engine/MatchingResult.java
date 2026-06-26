package com.tradepulse.matching.engine;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Outcome of a single matching cycle for one incoming order.
 */
public record MatchingResult(
        UUID orderId,
        UUID userId,
        String symbol,
        String side,
        BigDecimal filledQuantity,
        BigDecimal averageFillPrice,
        boolean fullyFilled,
        List<FillDetail> fills
) {
    public record FillDetail(
            UUID makerOrderId,
            UUID makerUserId,
            BigDecimal fillQuantity,
            BigDecimal fillPrice
    ) {}
}

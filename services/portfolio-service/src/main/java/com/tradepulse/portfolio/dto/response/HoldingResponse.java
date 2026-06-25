package com.tradepulse.portfolio.dto.response;

import java.math.BigDecimal;

public record HoldingResponse(
        String symbol,
        BigDecimal quantity,
        BigDecimal avgCostBasis,
        BigDecimal currentPrice,       // from Redis price:{SYMBOL}
        BigDecimal currentValue,       // quantity × currentPrice
        BigDecimal unrealizedPnl,      // currentValue - (quantity × avgCostBasis)
        BigDecimal unrealizedPnlPct
) {}

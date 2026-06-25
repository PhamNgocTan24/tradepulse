package com.tradepulse.portfolio.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PortfolioResponse(
        UUID userId,
        BigDecimal cashBalance,
        BigDecimal holdingsValue,     // sum of all holding.currentValue
        BigDecimal totalPortfolioValue, // cashBalance + holdingsValue
        BigDecimal totalUnrealizedPnl,
        List<HoldingResponse> holdings
) {}

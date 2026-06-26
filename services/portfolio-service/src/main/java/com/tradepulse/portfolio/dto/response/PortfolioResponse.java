package com.tradepulse.portfolio.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * HTTP response for GET /api/portfolio/me.
 *
 * <p>{@code totalRealizedPnl} is the cumulative sum of realized P&amp;L from all closed SELL fills.
 * {@code totalUnrealizedPnl} reflects the current mark-to-market gain/loss on open positions,
 * priced live from Redis ({@code price:{SYMBOL}}).
 */
public record PortfolioResponse(
        UUID userId,
        BigDecimal cashBalance,
        BigDecimal holdingsValue,         // sum of all holding.currentValue
        BigDecimal totalPortfolioValue,   // cashBalance + holdingsValue
        BigDecimal totalUnrealizedPnl,    // mark-to-market gain/loss on open positions
        BigDecimal totalRealizedPnl,      // cumulative P&L from closed SELL fills
        List<HoldingResponse> holdings
) {}


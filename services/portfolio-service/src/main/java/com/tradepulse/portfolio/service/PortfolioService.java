package com.tradepulse.portfolio.service;

import com.tradepulse.common.dto.kafka.portfolio.PortfolioEvent;
import com.tradepulse.common.dto.response.PageResponse;
import com.tradepulse.portfolio.dto.response.PortfolioResponse;
import com.tradepulse.portfolio.dto.response.TransactionResponse;

import java.util.UUID;

public interface PortfolioService {

    /** Returns current holdings + P&L (unrealized + realized). Reads prices from Redis only. */
    PortfolioResponse getPortfolio(UUID userId);

    /** Called by Kafka consumer on ORDER_FILLED / PARTIAL_FILL. */
    void applyFill(PortfolioEvent event);

    /**
     * Returns paginated transaction history as safe DTO views.
     * JPA {@link com.tradepulse.portfolio.domain.entity.Transaction} entities are never
     * exposed directly through this API.
     */
    PageResponse<TransactionResponse> getHistory(UUID userId, int page, int size);
}

package com.tradepulse.portfolio.service;

import com.tradepulse.common.dto.kafka.portfolio.PortfolioEvent;
import com.tradepulse.common.dto.response.PageResponse;
import com.tradepulse.portfolio.domain.entity.Transaction;
import com.tradepulse.portfolio.dto.response.PortfolioResponse;

import java.util.UUID;

public interface PortfolioService {

    /** Returns current holdings + P&L. Reads prices from Redis only. */
    PortfolioResponse getPortfolio(UUID userId);

    /** Called by Kafka consumer on ORDER_FILLED / PARTIAL_FILL. */
    void applyFill(PortfolioEvent event);

    PageResponse<Transaction> getHistory(UUID userId, int page, int size);
}

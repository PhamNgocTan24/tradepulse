package com.tradepulse.marketdata.service;

import com.tradepulse.marketdata.domain.entity.MarketTick;
import com.tradepulse.marketdata.domain.model.PriceInfo;

import java.math.BigDecimal;
import java.util.List;

public interface MarketDataService {

    /** Called by BinanceWebSocketClient on each incoming tick. */
    void processTick(MarketTick tick);

    /** Returns current price from Redis (TTL 30s). Never reads PostgreSQL. */
    BigDecimal getCurrentPrice(String symbol);

    /** Returns a snapshot of current price info from Redis. */
    PriceInfo getPriceInfo(String symbol);

    /** Returns recent tick history from MongoDB (paginated). */
    List<MarketTick> getTickHistory(String symbol, int limit);
}

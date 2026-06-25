package com.tradepulse.marketdata.domain.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable tick stored in MongoDB market_ticks collection.
 * Never updated — append-only time-series data.
 */
@Document(collection = "market_ticks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketTick {

    @Id
    private String id;

    @Indexed
    private String symbol;

    private BigDecimal price;
    private BigDecimal volume24h;
    private BigDecimal priceChangePercent24h;
    private BigDecimal highPrice24h;
    private BigDecimal lowPrice24h;

    @Indexed
    private Instant timestamp;

    /** Source: BINANCE or COINGECKO */
    private String source;
}

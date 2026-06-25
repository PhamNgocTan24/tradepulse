package com.tradepulse.marketdata.repository;

import com.tradepulse.marketdata.domain.entity.MarketTick;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface MarketTickRepository extends MongoRepository<MarketTick, String> {

    List<MarketTick> findBySymbolOrderByTimestampDesc(String symbol, Pageable pageable);

    List<MarketTick> findBySymbolAndTimestampBetweenOrderByTimestampAsc(
            String symbol, Instant from, Instant to);
}

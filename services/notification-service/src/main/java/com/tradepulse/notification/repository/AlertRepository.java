package com.tradepulse.notification.repository;

import com.tradepulse.notification.domain.entity.PriceAlert;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.UUID;

public interface AlertRepository extends MongoRepository<PriceAlert, String> {

    List<PriceAlert> findByUserIdAndActiveTrue(UUID userId);

    /** Used during market-data tick evaluation — only active, non-triggered alerts. */
    List<PriceAlert> findBySymbolAndActiveTrueAndTriggeredFalse(String symbol);
}

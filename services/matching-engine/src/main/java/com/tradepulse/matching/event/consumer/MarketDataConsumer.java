package com.tradepulse.matching.event.consumer;

import com.tradepulse.common.dto.kafka.market.MarketDataEvent;
import com.tradepulse.matching.service.MatchingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes market-data ticks to trigger limit order evaluation on price updates.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketDataConsumer {

    private final MatchingService matchingService;

    @KafkaListener(topics = "market-data",
                   groupId = "matching-engine-market",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onMarketData(MarketDataEvent event) {
        matchingService.onPriceUpdate(event.symbol(), event.price());
    }
}

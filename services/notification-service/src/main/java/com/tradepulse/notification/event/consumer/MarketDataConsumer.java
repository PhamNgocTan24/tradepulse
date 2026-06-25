package com.tradepulse.notification.event.consumer;

import com.tradepulse.common.dto.kafka.market.MarketDataEvent;
import com.tradepulse.notification.service.AlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes 'market-data' topic to evaluate price alerts on every tick.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketDataConsumer {

    private final AlertService alertService;

    @KafkaListener(topics = "market-data",
                   groupId = "notification-price-alert-checker",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onMarketData(MarketDataEvent event) {
        alertService.evaluateAlerts(event.symbol(), event.price());
    }
}

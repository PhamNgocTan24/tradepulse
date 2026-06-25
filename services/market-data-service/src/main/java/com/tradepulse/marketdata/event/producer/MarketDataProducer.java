package com.tradepulse.marketdata.event.producer;

import com.tradepulse.common.dto.kafka.market.MarketDataEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketDataProducer {

    private static final String TOPIC = "market-data";

    private final KafkaTemplate<String, MarketDataEvent> kafkaTemplate;

    /**
     * Publishes a market tick to the 'market-data' topic.
     * Partition key = symbol so matching-engine consumers get ordered ticks per symbol.
     */
    public void publish(MarketDataEvent event) {
        kafkaTemplate.send(TOPIC, event.symbol(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish market data: symbol={}", event.symbol(), ex);
                    } else {
                        log.debug("Published market tick: symbol={}, price={}", event.symbol(), event.price());
                    }
                });
    }
}

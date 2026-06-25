package com.tradepulse.portfolio.event.consumer;

import com.tradepulse.common.dto.kafka.portfolio.PortfolioEvent;
import com.tradepulse.portfolio.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes ORDER_FILLED / PARTIAL_FILL from 'portfolio-events' topic.
 * Partitioned by user_id — guarantees ordered processing per user.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PortfolioEventConsumer {

    private final PortfolioService portfolioService;

    @KafkaListener(topics = "portfolio-events",
                   groupId = "portfolio-service",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onPortfolioEvent(PortfolioEvent event) {
        log.info("Portfolio event received: type={}, userId={}, orderId={}",
                event.eventType(), event.userId(), event.orderId());
        portfolioService.applyFill(event);
    }
}

package com.tradepulse.portfolio.event.consumer;

import com.tradepulse.common.dto.kafka.portfolio.PortfolioEvent;
import com.tradepulse.portfolio.domain.entity.EventLog;
import com.tradepulse.portfolio.repository.EventLogRepository;
import com.tradepulse.portfolio.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes ORDER_FILLED / PARTIAL_FILL events from the 'portfolio-events' topic.
 *
 * <p>Partitioned by user_id — guarantees ordered processing per user.
 *
 * <p><b>Idempotency:</b> Each event is deduplicated by its {@code eventId} using the
 * {@code event_log} table (Pattern #3). A UNIQUE constraint prevents double-processing
 * on consumer rebalance or Kafka replay.
 *
 * <p><b>DLQ:</b> Failed events (e.g., missing portfolio account) are routed to
 * {@code portfolio-events-dlq} so the main consumer keeps progressing (Pattern #11).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PortfolioEventConsumer {

    private static final String DLQ_TOPIC = "portfolio-events-dlq";

    private final PortfolioService portfolioService;
    private final EventLogRepository eventLogRepository;
    private final KafkaTemplate<String, PortfolioEvent> portfolioEventKafkaTemplate;

    @KafkaListener(topics = "portfolio-events",
                   groupId = "portfolio-service",
                   containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void onPortfolioEvent(PortfolioEvent event) {
        log.info("Portfolio event received: type={}, userId={}, orderId={}, eventId={}",
                event.eventType(), event.userId(), event.orderId(), event.eventId());

        // Idempotency guard — skip if already processed
        if (eventLogRepository.existsByEventId(event.eventId())) {
            log.info("Duplicate event skipped: eventId={}", event.eventId());
            return;
        }

        try {
            portfolioService.applyFill(event);
            eventLogRepository.save(EventLog.builder()
                    .eventId(event.eventId())
                    .build());
            log.info("Portfolio event processed: eventId={}, userId={}", event.eventId(), event.userId());
        } catch (Exception ex) {
            log.error("Portfolio fill failed — routing to DLQ: eventId={}, userId={}, error={}",
                    event.eventId(), event.userId(), ex.getMessage(), ex);
            portfolioEventKafkaTemplate.send(DLQ_TOPIC, event.userId().toString(), event);
        }
    }
}

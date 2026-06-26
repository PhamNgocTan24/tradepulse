package com.tradepulse.portfolio.event.consumer;

import com.tradepulse.common.dto.kafka.portfolio.PortfolioEvent;
import com.tradepulse.common.dto.kafka.portfolio.PortfolioEventType;
import com.tradepulse.portfolio.domain.entity.EventLog;
import com.tradepulse.portfolio.repository.EventLogRepository;
import com.tradepulse.portfolio.service.PortfolioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PortfolioEventConsumer.
 *
 * <p>KafkaTemplate cannot be mocked or spied on Java 26+ with Mockito inline mocks.
 * We use a hand-written test double that simply records sent DLQ messages.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PortfolioEventConsumer Unit Tests")
class PortfolioEventConsumerTest {

    @Mock private PortfolioService portfolioService;
    @Mock private EventLogRepository eventLogRepository;

    /** Simple test double — records sent DLQ topic:key pairs. */
    private static class CapturingKafkaTemplate extends KafkaTemplate<String, PortfolioEvent> {
        final List<String> sent = new ArrayList<>();

        // Required by KafkaTemplate
        CapturingKafkaTemplate() {
            super(new org.springframework.kafka.core.DefaultKafkaProducerFactory<>(
                    java.util.Map.of(
                            org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9999",
                            org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                                    org.apache.kafka.common.serialization.StringSerializer.class,
                            org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                                    org.springframework.kafka.support.serializer.JsonSerializer.class
                    )
            ));
        }

        @Override
        public java.util.concurrent.CompletableFuture<
                org.springframework.kafka.support.SendResult<String, PortfolioEvent>>
        send(String topic, String key, PortfolioEvent value) {
            sent.add(topic + ":" + key);
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
    }

    private CapturingKafkaTemplate kafkaTemplate;
    private PortfolioEventConsumer consumer;

    private UUID userId;
    private UUID eventId;
    private PortfolioEvent event;

    @BeforeEach
    void setUp() {
        userId  = UUID.randomUUID();
        eventId = UUID.randomUUID();
        event = new PortfolioEvent(
                eventId, PortfolioEventType.ORDER_FILLED, UUID.randomUUID(), userId,
                "BTCUSDT", "BUY", new BigDecimal("0.50000000"), new BigDecimal("67000.00000000"),
                Instant.now());

        kafkaTemplate = new CapturingKafkaTemplate();
        consumer = new PortfolioEventConsumer(portfolioService, eventLogRepository, kafkaTemplate);
    }

    @Test
    @DisplayName("New event: calls applyFill and saves EventLog")
    void onPortfolioEvent_newEvent_processesAndLogsEventId() {
        given(eventLogRepository.existsByEventId(eventId)).willReturn(false);

        consumer.onPortfolioEvent(event);

        verify(portfolioService).applyFill(event);

        ArgumentCaptor<EventLog> logCaptor = ArgumentCaptor.forClass(EventLog.class);
        verify(eventLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getEventId()).isEqualTo(eventId);

        assertThat(kafkaTemplate.sent).isEmpty();
    }

    @Test
    @DisplayName("Duplicate event: skips applyFill when eventId already in EventLog")
    void onPortfolioEvent_duplicateEvent_skipsApplyFill() {
        given(eventLogRepository.existsByEventId(eventId)).willReturn(true);

        consumer.onPortfolioEvent(event);

        verifyNoInteractions(portfolioService);
        verify(eventLogRepository, never()).save(any());
        assertThat(kafkaTemplate.sent).isEmpty();
    }

    @Test
    @DisplayName("Exception during applyFill: routes to DLQ and does NOT save EventLog")
    void onPortfolioEvent_exceptionInApplyFill_routesToDlq() {
        given(eventLogRepository.existsByEventId(eventId)).willReturn(false);
        doThrow(new RuntimeException("Simulated DB failure")).when(portfolioService).applyFill(event);

        consumer.onPortfolioEvent(event);

        assertThat(kafkaTemplate.sent).hasSize(1);
        assertThat(kafkaTemplate.sent.get(0)).startsWith("portfolio-events-dlq:");
        verify(eventLogRepository, never()).save(any());
    }
}

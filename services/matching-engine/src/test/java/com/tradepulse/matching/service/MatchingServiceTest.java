package com.tradepulse.matching.service;

import com.tradepulse.common.dto.kafka.order.OrderEvent;
import com.tradepulse.common.dto.kafka.order.OrderEventType;
import com.tradepulse.matching.event.producer.OrderResultProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchingServiceTest {

    static {
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private OrderResultProducer resultProducer;
    private MatchingService matchingService;
    private final String symbol = "BTCUSDT";

    @BeforeEach
    void setUp() {
        // Construct the real producer with the mocked KafkaTemplate
        resultProducer = new OrderResultProducer(kafkaTemplate);
        matchingService = new MatchingService(resultProducer);
    }

    @Test
    void testProcessNewOrderAndMatch() {
        // Place resting Ask (SELL limit order: qty = 1.0, price = 60000.00)
        OrderEvent sellOrder = new OrderEvent(
                UUID.randomUUID(), OrderEventType.NEW_ORDER, UUID.randomUUID(), UUID.randomUUID(),
                symbol, "SELL", "LIMIT", new BigDecimal("1.0"), new BigDecimal("60000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, Instant.now()
        );
        matchingService.processNewOrder(sellOrder);

        // Place matching Bid (BUY limit order: qty = 1.0, price = 60000.00)
        OrderEvent buyOrder = new OrderEvent(
                UUID.randomUUID(), OrderEventType.NEW_ORDER, UUID.randomUUID(), UUID.randomUUID(),
                symbol, "BUY", "LIMIT", new BigDecimal("1.0"), new BigDecimal("60000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, Instant.now()
        );

        reset(kafkaTemplate);

        matchingService.processNewOrder(buyOrder);

        // Verify Kafka publishes the ORDER_FILLED event
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate, times(2)).send(anyString(), anyString(), eventCaptor.capture());

        // Check first published event is OrderEvent
        OrderEvent publishedOrderEvent = (OrderEvent) eventCaptor.getAllValues().get(0);
        assertEquals(buyOrder.orderId(), publishedOrderEvent.orderId());
        assertEquals(OrderEventType.ORDER_FILLED, publishedOrderEvent.eventType());
        assertEquals(0, new BigDecimal("1.0").compareTo(publishedOrderEvent.filledQuantity()));
        assertEquals(0, new BigDecimal("60000.00").compareTo(publishedOrderEvent.averageFillPrice()));
    }

    @Test
    void testOnPriceUpdateTriggersFills() {
        // Place resting BUY order at 60,000 (qty = 1.0, price = 60000.00)
        OrderEvent buyOrder = new OrderEvent(
                UUID.randomUUID(), OrderEventType.NEW_ORDER, UUID.randomUUID(), UUID.randomUUID(),
                symbol, "BUY", "LIMIT", new BigDecimal("1.0"), new BigDecimal("60000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, Instant.now()
        );
        matchingService.processNewOrder(buyOrder);

        reset(kafkaTemplate);

        // Price drops to 59,000
        matchingService.onPriceUpdate(symbol, new BigDecimal("59000.00"));

        // Verify Kafka publishes the ORDER_FILLED event
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate, times(2)).send(anyString(), anyString(), eventCaptor.capture());

        OrderEvent publishedOrderEvent = (OrderEvent) eventCaptor.getAllValues().get(0);
        assertEquals(buyOrder.orderId(), publishedOrderEvent.orderId());
        assertEquals(OrderEventType.ORDER_FILLED, publishedOrderEvent.eventType());
        assertEquals(0, new BigDecimal("1.0").compareTo(publishedOrderEvent.filledQuantity()));
        assertEquals(0, new BigDecimal("60000.00").compareTo(publishedOrderEvent.averageFillPrice()));
    }

    @Test
    void testProcessCancelOrder() {
        UUID orderId = UUID.randomUUID();
        OrderEvent sellOrder = new OrderEvent(
                UUID.randomUUID(), OrderEventType.NEW_ORDER, orderId, UUID.randomUUID(),
                symbol, "SELL", "LIMIT", new BigDecimal("1.0"), new BigDecimal("60000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, Instant.now()
        );
        matchingService.processNewOrder(sellOrder);

        OrderEvent cancelEvent = new OrderEvent(
                UUID.randomUUID(), OrderEventType.CANCEL_ORDER, orderId, UUID.randomUUID(),
                symbol, "SELL", "LIMIT", new BigDecimal("1.0"), new BigDecimal("60000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, Instant.now()
        );

        reset(kafkaTemplate);

        matchingService.processCancelOrder(cancelEvent);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), eventCaptor.capture());

        OrderEvent publishedEvent = (OrderEvent) eventCaptor.getValue();
        assertEquals(cancelEvent.orderId(), publishedEvent.orderId());
        assertEquals(OrderEventType.ORDER_CANCELLED, publishedEvent.eventType());
    }
}

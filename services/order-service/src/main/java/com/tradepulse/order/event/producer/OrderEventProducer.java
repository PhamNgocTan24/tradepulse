package com.tradepulse.order.event.producer;

import com.tradepulse.common.dto.kafka.order.OrderEvent;
import com.tradepulse.common.dto.kafka.order.OrderEventType;
import com.tradepulse.order.domain.entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventProducer {

    private static final String TOPIC = "order-events";

    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    /** Publishes NEW_ORDER after persisting to PostgreSQL. */
    public void publishNewOrder(Order order) {
        OrderEvent event = OrderEvent.newOrder(
                order.getId(), order.getUserId(), order.getSymbol(),
                order.getSide().name(), order.getOrderType().name(),
                order.getQuantity(), order.getPrice()
        );
        send(event);
    }

    /** Publishes CANCEL_ORDER — key = orderId for ordered delivery to matching-engine. */
    public void publishCancelOrder(UUID orderId, UUID userId, String symbol) {
        OrderEvent event = new OrderEvent(
                UUID.randomUUID(), OrderEventType.CANCEL_ORDER,
                orderId, userId, symbol, null, null,
                null, null, null, null,
                java.time.Instant.now()
        );
        send(event);
    }

    private void send(OrderEvent event) {
        kafkaTemplate.send(TOPIC, event.orderId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish order event: orderId={}, type={}",
                                event.orderId(), event.eventType(), ex);
                    } else {
                        log.info("Published order event: orderId={}, type={}",
                                event.orderId(), event.eventType());
                    }
                });
    }
}

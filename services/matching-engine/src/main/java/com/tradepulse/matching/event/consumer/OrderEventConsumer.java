package com.tradepulse.matching.event.consumer;

import com.tradepulse.common.dto.kafka.order.OrderEvent;
import com.tradepulse.common.dto.kafka.order.OrderEventType;
import com.tradepulse.matching.service.MatchingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes NEW_ORDER and CANCEL_ORDER from the 'order-events' topic.
 * Forwards to MatchingService — no DB calls in this path.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final MatchingService matchingService;

    @KafkaListener(topics = "order-events",
                   groupId = "matching-engine-orders",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onOrderEvent(OrderEvent event) {
        if (event.eventType() == OrderEventType.NEW_ORDER) {
            log.debug("Received NEW_ORDER: orderId={}, symbol={}", event.orderId(), event.symbol());
            matchingService.processNewOrder(event);
        } else if (event.eventType() == OrderEventType.CANCEL_ORDER) {
            log.debug("Received CANCEL_ORDER: orderId={}", event.orderId());
            matchingService.processCancelOrder(event);
        }
        // ORDER_FILLED, PARTIAL_FILL, ORDER_CANCELLED are produced BY this service — ignore
    }
}

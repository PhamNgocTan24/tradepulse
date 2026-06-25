package com.tradepulse.order.event.consumer;

import com.tradepulse.common.dto.kafka.order.OrderEvent;
import com.tradepulse.order.domain.entity.Order;
import com.tradepulse.order.domain.entity.OrderAuditLog;
import com.tradepulse.order.domain.enums.OrderStatus;
import com.tradepulse.order.repository.AuditLogRepository;
import com.tradepulse.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Consumes ORDER_FILLED, PARTIAL_FILL, ORDER_CANCELLED from matching-engine
 * and updates the order status in PostgreSQL + appends an audit log entry to MongoDB.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final OrderRepository orderRepository;
    private final AuditLogRepository auditLogRepository;

    @KafkaListener(topics = "order-events", groupId = "order-service-status-updater",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onOrderEvent(OrderEvent event) {
        switch (event.eventType()) {
            case ORDER_FILLED -> handleFill(event, OrderStatus.FILLED);
            case PARTIAL_FILL -> handleFill(event, OrderStatus.PARTIALLY_FILLED);
            case ORDER_CANCELLED -> handleCancel(event);
            default -> { /* NEW_ORDER / CANCEL_ORDER: not consumed here */ }
        }
    }

    private void handleFill(OrderEvent event, OrderStatus newStatus) {
        orderRepository.findById(event.orderId()).ifPresentOrElse(order -> {
            OrderStatus prev = order.getStatus();
            order.setStatus(newStatus);
            order.setFilledQuantity(event.filledQuantity());
            order.setAverageFillPrice(event.averageFillPrice());
            orderRepository.save(order);
            appendAudit(order, prev, newStatus, "MATCHING_ENGINE");
            log.info("Order {} → {}: orderId={}", prev, newStatus, event.orderId());
        }, () -> log.warn("Order not found for fill event: orderId={}", event.orderId()));
    }

    private void handleCancel(OrderEvent event) {
        orderRepository.findById(event.orderId()).ifPresentOrElse(order -> {
            OrderStatus prev = order.getStatus();
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);
            appendAudit(order, prev, OrderStatus.CANCELLED, "MATCHING_ENGINE");
            log.info("Order CANCELLED: orderId={}", event.orderId());
        }, () -> log.warn("Order not found for cancel event: orderId={}", event.orderId()));
    }

    private void appendAudit(Order order, OrderStatus prev, OrderStatus next, String source) {
        auditLogRepository.save(OrderAuditLog.builder()
                .orderId(order.getId())
                .userId(order.getUserId())
                .symbol(order.getSymbol())
                .side(order.getSide().name())
                .orderType(order.getOrderType().name())
                .quantity(order.getQuantity())
                .price(order.getPrice())
                .filledQuantity(order.getFilledQuantity())
                .averageFillPrice(order.getAverageFillPrice())
                .previousStatus(prev)
                .newStatus(next)
                .eventSource(source)
                .timestamp(Instant.now())
                .build());
    }
}

package com.tradepulse.order.service.impl;

import com.tradepulse.common.dto.response.PageResponse;
import com.tradepulse.order.domain.entity.Order;
import com.tradepulse.order.domain.entity.OrderAuditLog;
import com.tradepulse.order.domain.enums.OrderStatus;
import com.tradepulse.order.domain.enums.OrderType;
import com.tradepulse.order.dto.request.PlaceOrderRequest;
import com.tradepulse.order.dto.response.OrderResponse;
import com.tradepulse.order.event.producer.OrderEventProducer;
import com.tradepulse.order.exception.OrderNotFoundException;
import com.tradepulse.order.exception.OrderValidationException;
import com.tradepulse.order.repository.AuditLogRepository;
import com.tradepulse.order.repository.OrderRepository;
import com.tradepulse.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final AuditLogRepository auditLogRepository;
    private final OrderEventProducer orderEventProducer;

    @Override
    @Transactional
    public OrderResponse placeOrder(UUID userId, PlaceOrderRequest request) {
        if (request.orderType() == OrderType.LIMIT && request.price() == null) {
            throw new OrderValidationException("Price is required for LIMIT orders");
        }

        Order order = Order.builder()
                .userId(userId)
                .symbol(request.symbol().toUpperCase())
                .side(request.side())
                .orderType(request.orderType())
                .quantity(request.quantity())
                .price(request.price())
                .status(OrderStatus.PENDING)
                .build();

        orderRepository.save(order);

        // Append initial audit entry
        appendAudit(order, null, OrderStatus.PENDING, "USER_REQUEST");

        // Publish NEW_ORDER to Kafka → matching-engine picks it up
        orderEventProducer.publishNewOrder(order);

        log.info("Order placed: orderId={}, userId={}, symbol={}, side={}",
                order.getId(), userId, order.getSymbol(), order.getSide());
        return toResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(UUID orderId, UUID userId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() == OrderStatus.FILLED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new OrderValidationException("Cannot cancel order in status: " + order.getStatus());
        }

        OrderStatus prev = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        appendAudit(order, prev, OrderStatus.CANCELLED, "USER_REQUEST");
        orderEventProducer.publishCancelOrder(orderId, userId, order.getSymbol());

        log.info("Order cancelled: orderId={}, userId={}", orderId, userId);
        return toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId, UUID userId) {
        return toResponse(orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new OrderNotFoundException(orderId)));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listOrders(UUID userId, int page, int size) {
        Page<Order> pageResult = orderRepository.findByUserId(userId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.of(pageResult.getContent().stream().map(this::toResponse).toList(),
                page, size, pageResult.getTotalElements());
    }

    private void appendAudit(Order order, OrderStatus prev, OrderStatus next, String source) {
        auditLogRepository.save(OrderAuditLog.builder()
                .orderId(order.getId()).userId(order.getUserId())
                .symbol(order.getSymbol()).side(order.getSide().name())
                .orderType(order.getOrderType().name()).quantity(order.getQuantity())
                .price(order.getPrice()).filledQuantity(order.getFilledQuantity())
                .averageFillPrice(order.getAverageFillPrice())
                .previousStatus(prev).newStatus(next).eventSource(source)
                .timestamp(Instant.now()).build());
    }

    private OrderResponse toResponse(Order o) {
        return new OrderResponse(o.getId(), o.getUserId(), o.getSymbol(), o.getSide(),
                o.getOrderType(), o.getQuantity(), o.getPrice(), o.getFilledQuantity(),
                o.getAverageFillPrice(), o.getStatus(), o.getCreatedAt(), o.getUpdatedAt());
    }
}

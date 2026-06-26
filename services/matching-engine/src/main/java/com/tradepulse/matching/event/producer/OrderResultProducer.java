package com.tradepulse.matching.event.producer;

import com.tradepulse.common.dto.kafka.order.OrderEvent;
import com.tradepulse.common.dto.kafka.order.OrderEventType;
import com.tradepulse.common.dto.kafka.portfolio.PortfolioEvent;
import com.tradepulse.common.dto.kafka.portfolio.PortfolioEventType;
import com.tradepulse.matching.engine.MatchingResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderResultProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /** Publishes ORDER_FILLED or PARTIAL_FILL to order-events + portfolio-events. */
    public void publishResult(MatchingResult result, OrderEvent original) {
        if (result.filledQuantity().compareTo(java.math.BigDecimal.ZERO) == 0) {
            return; // nothing matched; order is resting in book
        }

        OrderEventType type = result.fullyFilled() ? OrderEventType.ORDER_FILLED
                                                    : OrderEventType.PARTIAL_FILL;

        OrderEvent fillEvent = new OrderEvent(
                UUID.randomUUID(), type, result.orderId(), result.userId(),
                result.symbol(), original.side(), original.orderType(),
                original.quantity(), original.price(),
                result.filledQuantity(), result.averageFillPrice(), Instant.now()
        );

        // Notify order-service to update status
        kafkaTemplate.send("order-events", result.orderId().toString(), fillEvent);

        // Notify portfolio-service to update holdings and balance
        PortfolioEventType portfolioType = result.fullyFilled()
                ? PortfolioEventType.ORDER_FILLED : PortfolioEventType.PARTIAL_FILL;

        PortfolioEvent portfolioEvent = new PortfolioEvent(
                UUID.randomUUID(), portfolioType, result.orderId(), result.userId(),
                result.symbol(), original.side(),
                result.filledQuantity(), result.averageFillPrice(), Instant.now()
        );
        kafkaTemplate.send("portfolio-events", result.userId().toString(), portfolioEvent);

        log.info("Result published: orderId={}, type={}, filled={}, avgPrice={}",
                result.orderId(), type, result.filledQuantity(), result.averageFillPrice());
    }

    /** Publishes ORDER_CANCELLED to order-events. */
    public void publishCancelled(OrderEvent original) {
        OrderEvent cancelledEvent = new OrderEvent(
                UUID.randomUUID(), OrderEventType.ORDER_CANCELLED,
                original.orderId(), original.userId(), original.symbol(),
                original.side(), original.orderType(), original.quantity(),
                original.price(), original.filledQuantity(), original.averageFillPrice(),
                Instant.now()
        );
        kafkaTemplate.send("order-events", original.orderId().toString(), cancelledEvent);
    }

    /** Publishes fill events for resting limit orders matched on price updates. */
    public void publishLimitOrderFill(MatchingResult result) {
        OrderEventType type = OrderEventType.ORDER_FILLED;

        OrderEvent fillEvent = new OrderEvent(
                UUID.randomUUID(), type, result.orderId(), result.userId(),
                result.symbol(), result.side(), "LIMIT",
                result.filledQuantity(), result.averageFillPrice(),
                result.filledQuantity(), result.averageFillPrice(), Instant.now()
        );

        // Notify order-service to update status
        kafkaTemplate.send("order-events", result.orderId().toString(), fillEvent);

        // Notify portfolio-service to update holdings and balance
        com.tradepulse.common.dto.kafka.portfolio.PortfolioEventType portfolioType = 
                com.tradepulse.common.dto.kafka.portfolio.PortfolioEventType.ORDER_FILLED;

        PortfolioEvent portfolioEvent = new PortfolioEvent(
                UUID.randomUUID(), portfolioType, result.orderId(), result.userId(),
                result.symbol(), result.side(),
                result.filledQuantity(), result.averageFillPrice(), Instant.now()
        );
        kafkaTemplate.send("portfolio-events", result.userId().toString(), portfolioEvent);

        log.info("Result published for limit order fill: orderId={}, type={}, filled={}, avgPrice={}",
                result.orderId(), type, result.filledQuantity(), result.averageFillPrice());
    }
}

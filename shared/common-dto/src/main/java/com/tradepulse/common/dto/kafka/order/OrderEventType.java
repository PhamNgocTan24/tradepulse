package com.tradepulse.common.dto.kafka.order;

/**
 * Event types flowing through the 'order-events' Kafka topic.
 * Direction:
 *   NEW_ORDER        → order-service → matching-engine (triggers matching)
 *   CANCEL_ORDER     → order-service → matching-engine (cancel request)
 *   ORDER_FILLED     ← matching-engine → order-service, portfolio-service
 *   PARTIAL_FILL     ← matching-engine → order-service, portfolio-service
 *   ORDER_CANCELLED  ↔ bidirectional (user cancel or engine cancel)
 */
public enum OrderEventType {
    NEW_ORDER,
    CANCEL_ORDER,
    ORDER_FILLED,
    PARTIAL_FILL,
    ORDER_CANCELLED
}

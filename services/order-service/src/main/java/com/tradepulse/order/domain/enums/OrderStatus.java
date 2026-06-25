package com.tradepulse.order.domain.enums;

public enum OrderStatus {
    PENDING,       // validated, stored, waiting for matching-engine
    OPEN,          // acknowledged by matching-engine, sitting in order book
    PARTIALLY_FILLED,
    FILLED,
    CANCELLED,
    REJECTED
}

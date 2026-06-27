# TradePulse — Order Placement, Matching & Execution Flow

This document details the lifecycle of an order from submission to matching, ledger updates, and client notifications.

## 1. End-to-End Order Processing Flow

```mermaid
sequenceDiagram
    autonumber
    actor Client as Client App
    participant Gateway as api-gateway-service (8080)
    participant OrderSvc as order-service (8084)
    participant PostgresOrder as PostgreSQL (order_db)
    participant MongoAudit as MongoDB (order_audit_log)
    participant Kafka as Apache Kafka (MSK)
    participant Engine as matching-engine (8086)
    participant PortfolioSvc as portfolio-service (8085)
    participant Redis as Redis Cache
    participant NotificationSvc as notification-service (8087)

    Note over Client, OrderSvc: Step 1: Placement & Persistence
    Client->>Gateway: POST /api/v1/orders {symbol, side, qty, type, price}
    Gateway->>OrderSvc: Forward with X-User-Id
    
    OrderSvc->>OrderSvc: Validate order parameters
    OrderSvc->>PostgresOrder: INSERT Order record (status: PENDING)
    OrderSvc->>MongoAudit: INSERT Audit log (type: ORDER_PLACED)
    OrderSvc->>Kafka: Publish NEW_ORDER to order-events topic (key: order_id)
    OrderSvc-->>Client: Return 201 Created with PENDING order status

    Note over Kafka, Engine: Step 2: Order Matching
    Kafka-->>Engine: Consume NEW_ORDER event
    
    alt OrderType == MARKET
        Engine->>Redis: Fetch latest price:price:{SYMBOL} (if no rest order)
        Engine->>Engine: Match against opposite resting orders or latest price
    else OrderType == LIMIT
        Engine->>Engine: Match against in-memory OrderBook (Price-Time Priority)
        alt Not fully matched
            Engine->>Engine: Store remaining quantity in memory OrderBook
        end
    end
    
    Engine->>Kafka: Publish ORDER_FILLED / PARTIAL_FILL to order-events topic (key: order_id)
    Engine->>Kafka: Publish PortfolioEvent to portfolio-events topic (key: user_id)

    Note over Kafka, PortfolioSvc: Step 3: Downstream Processing & Settlement
    par Order Status Update
        Kafka-->>OrderSvc: Consume ORDER_FILLED / PARTIAL_FILL
        OrderSvc->>PostgresOrder: UPDATE status to FILLED / PARTIALLY_FILLED
        OrderSvc->>MongoAudit: INSERT Audit log (type: ORDER_FILLED)
    and Portfolio Settlement
        Kafka-->>PortfolioSvc: Consume PortfolioEvent
        PortfolioSvc->>PortfolioSvc: Check eventId in PostgreSQL (Idempotency check)
        alt Event is new
            PortfolioSvc->>PortfolioSvc: Calculate cost basis & P&L
            PortfolioSvc->>PortfolioSvc: Update cash balance & asset holdings (PostgreSQL)
            PortfolioSvc->>PortfolioSvc: Save transaction record
            PortfolioSvc->>Redis: Update ZSet leaderboard score (virtual balance + asset values)
            PortfolioSvc->>Redis: Cache new portfolio value (TTL 60s)
            PortfolioSvc->>PortfolioSvc: Log eventId in PostgreSQL event_log table
        else Event is duplicate
            Note over PortfolioSvc: Skip processing
        end
    and Client Notifications
        Kafka-->>NotificationSvc: Consume events
        NotificationSvc->>NotificationSvc: Save alert/notification history (MongoDB)
        NotificationSvc->>NotificationSvc: Send Push Notification (WebSocket STOMP)
        NotificationSvc->>NotificationSvc: Trigger transactional email (AWS SES)
    end
```

## 2. matching-engine Matching Flowchart

```mermaid
graph TD
    A[NEW_ORDER Event Received] --> B{Order Type?}
    
    B -->|MARKET| C{Check opposite rest orders in OrderBook?}
    C -->|Yes| D[Execute match at resting price]
    C -->|No| E[Fetch current asset price from Redis]
    E --> F[Execute match at Redis price]
    
    B -->|LIMIT| G{Compare limit price with opposite best price}
    G -->|Buy Price >= Best Ask OR Sell Price <= Best Bid| H[Match and Fill order]
    G -->|Price does not cross| I[Insert order into Bid/Ask PriorityQueue]
    
    D --> J[Generate fills list]
    F --> J
    H --> K{Remaining quantity > 0?}
    K -->|Yes| L{Order Type?}
    L -->|MARKET| M[Cancel remaining quantity]
    L -->|LIMIT| N[Insert remaining resting order into OrderBook]
    K -->|No| O[Order fully filled]
    
    J --> P[Construct MatchingResult]
    M --> P
    N --> P
    O --> P
    
    P --> Q[Publish order-events: ORDER_FILLED / PARTIAL_FILL]
    P --> R[Publish portfolio-events]
```

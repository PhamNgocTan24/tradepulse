# TradePulse — System Architecture Diagram

This diagram visualizes the structural components of the TradePulse system, detailing microservices, external dependencies, data storage choices, and async event streams.

```mermaid
graph TD
    classDef client fill:#e1f5fe,stroke:#0288d1,stroke-width:2px,color:#000;
    classDef gateway fill:#e8f5e9,stroke:#2e7d32,stroke-width:2px,color:#000;
    classDef service fill:#ede7f6,stroke:#651fff,stroke-width:1px,color:#000;
    classDef db fill:#fffde7,stroke:#fbc02d,stroke-width:1px,color:#000;
    classDef kafka fill:#fbe9e7,stroke:#ff5722,stroke-width:1px,color:#000;
    classDef external fill:#eceff1,stroke:#607d8b,stroke-width:1px,color:#000;

    %% 1. Ingress Layer
    Client("Client Apps<br/>(React / Postman)"):::client
    Binance("Binance WebSocket API"):::external

    %% 2. API Gateway & Central Rate Limiting
    subgraph GatewayGroup ["API Gateway Layer"]
        GatewaySvc("api-gateway-service<br/>(Port 8080)"):::gateway
        GW_Redis[("Redis<br/>(rate_limit)")]:::db
        GatewaySvc <--> GW_Redis
    end
    Client -->|REST API - JWT Bearer| GatewaySvc

    %% 3. Apache Kafka Event Bus (Central Axis)
    subgraph Kafka ["Apache Kafka Event Broker"]
        MD_Topic("market-data topic<br/>(10 partitions, key: symbol)"):::kafka
        OE_Topic("order-events topic<br/>(5 partitions, key: order_id)"):::kafka
        PE_Topic("portfolio-events topic<br/>(5 partitions, key: user_id)"):::kafka
        N_Topic("notifications topic<br/>(3 partitions, key: user_id)"):::kafka
    end

    %% 4. Microservices Layer (Each microservice isolated with its own database to prevent cross-db calls)
    subgraph Services ["Microservices & Databases"]
        
        subgraph MDGroup ["market-data-service (8083)"]
            MDSvc("market-data-service"):::service
            MDMongo[("MongoDB<br/>(ticks)")]:::db
            MDRedis[("Redis<br/>(price cache)")]:::db
            MDSvc --> MDMongo
            MDSvc --> MDRedis
        end

        subgraph AuthGroup ["auth-service (8081)"]
            AuthSvc("auth-service"):::service
            AuthDB[("PostgreSQL<br/>(auth_db)")]:::db
            AuthRedis[("Redis<br/>(blacklist)")]:::db
            AuthSvc --> AuthDB
            AuthSvc --> AuthRedis
        end

        subgraph UserGroup ["user-service (8082)"]
            UserSvc("user-service"):::service
            UserDB[("PostgreSQL<br/>(user_db)")]:::db
            UserSvc --> UserDB
        end

        subgraph OrderGroup ["order-service (8084)"]
            OrderSvc("order-service"):::service
            OrderDB[("PostgreSQL<br/>(order_db)")]:::db
            OrderMongo[("MongoDB<br/>(audit log)")]:::db
            OrderSvc --> OrderDB
            OrderSvc --> OrderMongo
        end

        subgraph PortGroup ["portfolio-service (8085)"]
            PortSvc("portfolio-service"):::service
            PortDB[("PostgreSQL<br/>(portfolio_db)")]:::db
            PortRedis[("Redis<br/>(leaderboard)")]:::db
            PortSvc --> PortDB
            PortSvc --> PortRedis
        end

        subgraph EngineGroup ["matching-engine (8086)"]
            Engine("matching-engine<br/>(Stateless)"):::service
        end

        subgraph NotifGroup ["notification-service (8087)"]
            NotifSvc("notification-service"):::service
            NotifMongo[("MongoDB<br/>(alerts)")]:::db
            NotifSvc --> NotifMongo
        end

        subgraph ReportGroup ["reporting-service (8088)"]
            ReportSvc("reporting-service"):::service
            ReportDB[("PostgreSQL<br/>(reporting_db)")]:::db
            ReportSvc --> ReportDB
        end

    end

    %% Gateway Routing (Dashed lines to look clean and separate from data flows)
    GatewaySvc -.->|Route| AuthSvc
    GatewaySvc -.->|Route| UserSvc
    GatewaySvc -.->|Route| MDSvc
    GatewaySvc -.->|Route| OrderSvc
    GatewaySvc -.->|Route| PortSvc
    GatewaySvc -.->|Route| ReportSvc
    GatewaySvc -.->|Route| NotifSvc

    %% Real-time / Event-driven Communication Paths
    Client <--->|STOMP WebSockets| MDSvc
    Binance ---> MDSvc
    
    %% Market Data Flow
    MDSvc -->|Publish ticks| MD_Topic
    MD_Topic --> Engine
    MD_Topic --> NotifSvc

    %% Order Execution Flow
    OrderSvc -->|Publish NEW_ORDER| OE_Topic
    OE_Topic --> Engine
    Engine -->|Publish FILLED/CANCELLED| OE_Topic
    OE_Topic --> OrderSvc
    OE_Topic --> PortSvc

    %% Portfolio & Settlement Flow
    Engine -->|Publish trade events| PE_Topic
    PE_Topic --> PortSvc

    %% Alerts & Email Flow
    NotifSvc -->|Publish alerts| N_Topic
    N_Topic --> NotifSvc
    NotifSvc -.->|Send Email| SES("AWS SES"):::external
```

### Components Summary

1. **api-gateway-service**: Serves as the single entry point. Centralizes JWT verification, CORS policy, and Redis-backed rate limiting.
2. **auth-service**: Manages user registration, JWT generation (RS256), token refreshing, logout blacklisting (Redis), and TOTP 2FA. Exposes a JWKS endpoint for gateway validation.
3. **user-service**: Handles user profiles, virtual balance, and displays the global leaderboard fetched from Redis.
4. **market-data-service**: Subscribes to Binance WebSocket. Normalizes ticks, stores history in MongoDB, caches real-time price in Redis, publishes to Kafka, and broadcasts to user UI via STOMP WebSocket.
5. **order-service**: Validates and creates orders in PostgreSQL, publishes order events to Kafka, and writes append-only event logs in MongoDB.
6. **matching-engine**: A fast, memory-based, stateless matching engine that processes buy/sell limit and market orders using the Kafka `market-data` and `order-events` streams.
7. **portfolio-service**: Tracks user holdings, calculates real-time profit and loss (P&L) using prices from Redis, and maintains the global leaderboard in a Redis Sorted Set (`ZSet`).
8. **notification-service**: Manages price alerts (MongoDB), listens for system events (Kafka), and sends transactional notifications (STOMP WebSocket & AWS SES).
9. **reporting-service**: Compiles PDF statements using iText7 and handles uploads/downloads via AWS S3 presigned URLs.

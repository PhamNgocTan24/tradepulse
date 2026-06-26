# TradePulse — Architecture

> **Quick links:** [Patterns](../development/Patterns.md) · [Syntax](../development/Syntax.md) · [API Contracts](../api/ApiContracts.md) · [Runbook](../operations/Runbook.md) · [Backlog](../planning/backlog.md) · [Roadmap](../planning/roadmap.md) · [Tasks](../planning/tasks.md)

---

## 1. Overview

TradePulse is a portfolio-grade, production-like crypto trading simulation platform where users receive **$100,000 virtual capital** and trade real-market-priced crypto assets in real time. The system demonstrates enterprise-level backend engineering across security, messaging, caching, polyglot persistence, and cloud-native deployment.

**Primary Goal:** Showcase senior-level backend engineering across the full required tech stack in one cohesive, justifiable system.

**Key characteristics:**
- Real-time price data ingested from Binance public WebSocket streams
- Event-driven microservices communicating via Apache Kafka
- Polyglot persistence: PostgreSQL (transactional), MongoDB (document/audit), Redis (cache/leaderboard)
- RS256 JWT authentication centralised at the API Gateway
- AWS-native production deployment (EKS, RDS, ElastiCache, MSK, S3, SES)

---

## 2. System Diagram

```
                        ┌─────────────────────────────────────────┐
                        │            AWS Cloud (EKS)               │
                        │                                          │
  Client (React/        │   ┌──────────────────────────────────┐   │
  Postman/curl)  ──────►│   │     API Gateway Service          │   │
                        │   │  (Spring Cloud Gateway + JWT     │   │
                        │   │   filter + rate limiter)         │   │
                        │   └──────┬───────────────────────────┘   │
                        │          │  routes to:                   │
                        │   ┌──────▼──────┐  ┌──────────────────┐  │
                        │   │ auth-service│  │ user-service     │  │
                        │   │ (port 8081) │  │ (port 8082)      │  │
                        │   └─────────────┘  └──────────────────┘  │
                        │   ┌─────────────┐  ┌──────────────────┐  │
                        │   │market-data- │  │ order-service    │  │
                        │   │service      │  │ (port 8084)      │  │
                        │   │(port 8083)  │  └────────┬─────────┘  │
                        │   └──────┬──────┘           │            │
                        │          │                  │            │
                        │   ┌──────▼──────────────────▼─────────┐  │
                        │   │          Apache Kafka (MSK)        │  │
                        │   │  topics: market-data, order-events │  │
                        │   │          notifications, portfolio   │  │
                        │   └──────┬──────────────────┬─────────┘  │
                        │          │                  │            │
                        │   ┌──────▼──────┐  ┌────────▼─────────┐  │
                        │   │matching-    │  │ portfolio-service │  │
                        │   │engine       │  │ (port 8085)      │  │
                        │   │(port 8086)  │  └──────────────────┘  │
                        │   └─────────────┘                        │
                        │   ┌─────────────┐  ┌──────────────────┐  │
                        │   │notification-│  │ reporting-service│  │
                        │   │service      │  │ (port 8087)      │  │
                        │   │(port 8088)  │  └──────────────────┘  │
                        │   └─────────────┘                        │
                        │                                          │
                        │   ┌──────────┐ ┌───────┐ ┌───────────┐  │
                        │   │PostgreSQL│ │MongoDB│ │  Redis     │  │
                        │   │  (RDS)   │ │(Atlas │ │(ElastiCache│  │
                        │   │          │ │ /EC2) │ │  Cluster)  │  │
                        │   └──────────┘ └───────┘ └───────────┘  │
                        └─────────────────────────────────────────┘
```

---

## 3. Service Catalogue

> **⚠️ Added beyond plan:** Consolidated table format with port, Maven module, Java package, primary DB, Kafka role — not present in any single source file.

| Service | Port | Maven Module | Package | Primary DB | Kafka Role | Notes |
|---|---|---|---|---|---|---|
| api-gateway-service | 8080 | `services/api-gateway-service` | `com.tradepulse.gateway` | Redis (rate limit) | — | WebFlux/Netty |
| auth-service | 8081 | `services/auth-service` | `com.tradepulse.auth` | PostgreSQL | — | JWT issue/refresh, OAuth2, TOTP |
| user-service | 8082 | `services/user-service` | `com.tradepulse.user` | PostgreSQL | — | Profiles, virtual balance |
| market-data-service | 8083 | `services/market-data-service` | `com.tradepulse.marketdata` | MongoDB + Redis | Producer: `market-data` | Dual transport: WebClient + STOMP |
| order-service | 8084 | `services/order-service` | `com.tradepulse.order` | PostgreSQL + MongoDB | Producer+Consumer: `order-events` | Audit log in MongoDB |
| portfolio-service | 8085 | `services/portfolio-service` | `com.tradepulse.portfolio` | PostgreSQL + Redis | Consumer: `portfolio-events`, `order-events` | Leaderboard ZSET |
| matching-engine | 8086 | `services/matching-engine` | `com.tradepulse.matching` | — (stateless) | Consumer: `order-events`, `market-data`; Producer: `order-events`, `portfolio-events` | No DB in hot path, <100ms p99 |
| notification-service | 8087 | `services/notification-service` | `com.tradepulse.notification` | MongoDB | Consumer: `notifications` | AWS SES email, WebSocket push |
| reporting-service | 8088 | `services/reporting-service` | `com.tradepulse.reporting` | PostgreSQL | — | PDF via iText7, S3 upload |

### Shared Modules

| Module | Package | Purpose |
|---|---|---|
| `shared/common-dto` | `com.tradepulse.common.dto` | Kafka event schemas, cross-service DTOs |
| `shared/common-security` | `com.tradepulse.security` | JWT claim models, token parsing helpers |

### Service Endpoints Reference

#### auth-service (`/api/v1/auth`)
- `POST /register` — register user
- `POST /login` — issue JWT pair
- `POST /refresh` — rotate tokens
- `POST /logout` — blacklist `jti`
- `GET  /oauth2/google` — Google OAuth2 redirect
- `POST /2fa/enable` — TOTP QR provisioning
- `POST /2fa/verify` — activate 2FA
- `GET  /.well-known/jwks.json` — public key for Gateway JWT validation

#### user-service (`/api/v1/users`)
- `GET  /me` — user profile
- `PUT  /me` — update profile
- `GET  /me/balance` — virtual USD balance
- `GET  /leaderboard` — top 100 from Redis ZSet

#### market-data-service (`/api/v1/market`)
- `GET  /prices` — all tracked assets from Redis
- `GET  /prices/{symbol}` — single asset
- `GET  /history/{symbol}?interval=1h&limit=100` — from MongoDB
- `WS   /ws/market` — STOMP WebSocket, subscribe to `/topic/prices`

#### order-service (`/api/v1/orders`)
- `POST /` — place order (MARKET / LIMIT / STOP_LOSS)
- `DELETE /{id}` — cancel pending order
- `GET  /` — paginated history (filter: status, symbol, type)
- `GET  /{id}` — single order detail

#### portfolio-service (`/api/v1/portfolio`)
- `GET  /me` — holdings + unrealized P&L
- `GET  /me/history` — P&L history

#### notification-service (`/api/v1/notifications`)
- `GET  /me` — notification history
- `POST /preferences` — update preferences
- `POST /alerts` — create price alert
- `DELETE /alerts/{id}`
- `GET  /alerts` — list active alerts

#### reporting-service (`/api/v1/reports`)
- `POST /portfolio` — trigger async PDF generation
- `GET  /{reportId}` — status + S3 presigned URL

---

## 4. Kafka Topics & Event Flows

### Topics

| Topic | Key | Partitions | Producers | Consumers |
|---|---|---|---|---|
| `market-data` | `symbol` | 10 | market-data-service | matching-engine, notification-service |
| `order-events` | `order_id` | 5 | order-service, matching-engine | order-service, portfolio-service |
| `portfolio-events` | `user_id` | 5 | matching-engine | portfolio-service |
| `notifications` | `user_id` | 3 | notification-service | notification-service |

### Event Types in `order-events`

| event_type | Direction | Description |
|---|---|---|
| `NEW_ORDER` | → engine | Triggers matching |
| `ORDER_FILLED` | ← engine | Full fill complete |
| `PARTIAL_FILL` | ← engine | Partial quantity matched |
| `ORDER_CANCELLED` | bidirectional | User cancel or engine cancel |

### Topic Schemas

```
Topic: market-data        (partitioned by symbol, 10 partitions)
  Key: symbol
  Value: { symbol, price, volume, change_pct, timestamp }

Topic: order-events       (partitioned by order_id, 5 partitions)
  Key: order_id
  Value: { event_type, order_id, user_id, symbol, side, qty, price, timestamp }

Topic: portfolio-events   (partitioned by user_id, 5 partitions)
  Key: user_id
  Value: { user_id, order_id, symbol, side, qty, fill_price, timestamp }

Topic: notifications      (partitioned by user_id, 3 partitions)
  Key: user_id
  Value: { user_id, type, title, body, metadata, timestamp }
```

### Event Flow Diagrams

```
[Market Order Flow]
User POST /api/orders
  → order-service validates & saves (PENDING)
  → publishes NEW_ORDER to order-events
  → matching-engine consumes NEW_ORDER
  → matches against order book (or market price from Redis)
  → publishes ORDER_FILLED to order-events + portfolio-events
  → order-service consumes ORDER_FILLED → updates status to FILLED
  → portfolio-service consumes portfolio-events → updates holdings + balance
  → portfolio-service updates Redis leaderboard (ZADD)
  → notification-service consumes → sends WebSocket push + email

[Limit Order Flow]
  → Same as above but matching-engine holds order in OrderBook
  → When market-data-service receives price update from Binance WebSocket stream
  → market-data-service publishes to market-data Kafka topic
  → matching-engine consumes market-data to re-evaluate limit orders
  → Triggers ORDER_FILLED when price condition met

[Price Alert Flow]
  → notification-service consumes market-data topic
  → Checks MongoDB price_alerts against new prices
  → If triggered: publish to notifications topic → WebSocket push + AWS SES email

[Market Data Streaming Flow]
Binance WebSocket (wss://stream.binance.com:9443)
  → market-data-service maintains persistent WebSocket connections
  → receives real-time price ticks (ticker events)
  → normalizes to internal MarketTick domain object
  → saves tick to MongoDB market_ticks collection
  → caches latest price in Redis (price:{SYMBOL}, TTL 30s)
  → publishes to Kafka market-data topic (key = symbol)
  → pushes to client WebSocket subscribers via STOMP /topic/prices
```

---

## 5. Binance WebSocket Integration

> **⚠️ Added beyond plan:** Dedicated section combining CLAUDE.md "Binance WebSocket" section and tradepulse.md §4.4 — not listed as a separate section in the plan.

**Endpoint:** `wss://stream.binance.com:9443`

**Tracked Streams:**
- `btcusdt@ticker`
- `ethusdt@ticker`
- `solusdt@ticker`

**Inbound Flow:**
```
Binance WebSocket
  ↓
market-data-service (websocket/BinanceWebSocketClient.java)
  ↓ Anti-Corruption Layer: normalize to MarketTick domain object
  ├── MongoDB: persist to market_ticks collection
  ├── Redis: SET price:{SYMBOL} (TTL 30s)
  ├── Kafka: publish to market-data topic (key = symbol)
  └── STOMP: push to /topic/prices WebSocket subscribers
```

**Reconnection & Resilience:**
- Exponential backoff: 1s → 2s → 4s → 8s → max 30s
- Circuit breaker: Resilience4j, threshold = 5 consecutive failures
- Health indicator: Mark service unhealthy if disconnected > 60s
- Implementation class: `com.tradepulse.marketdata.websocket.BinanceWebSocketClient`

**Transport Stack in market-data-service:**
- Inbound (Binance): WebFlux `WebClient` (reactive)
- Outbound (clients): Spring WebSocket STOMP over SockJS

---

## 6. Database Design

### 6.1 PostgreSQL — Transactional Data

```sql
-- auth-service DB
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255),
    oauth_provider VARCHAR(50),
    oauth_id       VARCHAR(255),
    totp_secret    VARCHAR(255),       -- AES-256 encrypted at rest
    totp_enabled   BOOLEAN DEFAULT FALSE,
    created_at    TIMESTAMP DEFAULT NOW(),
    updated_at    TIMESTAMP DEFAULT NOW()
);

-- user-service DB
CREATE TABLE accounts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL UNIQUE,
    virtual_balance DECIMAL(18, 8) NOT NULL DEFAULT 100000.00,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

-- order-service DB
CREATE TABLE orders (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID NOT NULL,
    symbol         VARCHAR(20) NOT NULL,
    order_type     VARCHAR(20) NOT NULL, -- MARKET, LIMIT, STOP_LOSS
    side           VARCHAR(10) NOT NULL, -- BUY, SELL
    quantity       DECIMAL(18, 8) NOT NULL,
    price          DECIMAL(18, 8),       -- NULL for market orders
    filled_qty     DECIMAL(18, 8) DEFAULT 0,
    avg_fill_price DECIMAL(18, 8),
    status         VARCHAR(20) NOT NULL, -- PENDING, PARTIAL, FILLED, CANCELLED
    created_at     TIMESTAMP DEFAULT NOW(),
    updated_at     TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_symbol_status ON orders(symbol, status);

-- portfolio-service DB
CREATE TABLE holdings (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL,
    symbol     VARCHAR(20) NOT NULL,
    quantity   DECIMAL(18, 8) NOT NULL,
    avg_cost   DECIMAL(18, 8) NOT NULL,
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(user_id, symbol)
);

-- portfolio-service DB — immutable ledger
CREATE TABLE transactions (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL,
    order_id     UUID NOT NULL,
    symbol       VARCHAR(20) NOT NULL,
    side         VARCHAR(10) NOT NULL,
    quantity     DECIMAL(18, 8) NOT NULL,
    price        DECIMAL(18, 8) NOT NULL,
    total_value  DECIMAL(18, 8) NOT NULL,
    realized_pnl DECIMAL(18, 8),
    executed_at  TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_transactions_user_id ON transactions(user_id);
```

**Decimal rule:** All monetary values use `DECIMAL(18, 8)` in SQL and `BigDecimal` in Java. Never `double` or `float`.

### 6.2 MongoDB — Flexible / High-Volume Data

```js
// market_ticks collection (market-data-service)
// Index: { symbol: 1, timestamp: -1 }
{
  _id: ObjectId,
  symbol: "BTCUSDT",
  price: 67450.30,
  volume: 15678.234,
  change_pct: 2.14,
  source: "binance",
  timestamp: ISODate("2024-01-15T10:30:00Z")
}

// order_audit_log collection (order-service) — APPEND-ONLY
{
  _id: ObjectId,
  order_id: "uuid",
  user_id: "uuid",
  event_type: "ORDER_PLACED" | "ORDER_FILLED" | "ORDER_CANCELLED",
  snapshot: { /* full order state at event time */ },
  timestamp: ISODate
}

// notifications collection (notification-service)
{
  _id: ObjectId,
  user_id: "uuid",
  type: "ORDER_FILLED" | "PRICE_ALERT",
  title: "BTCUSDT order filled",
  body: "Bought 0.5 BTC at $67,450.30",
  read: false,
  created_at: ISODate
}

// price_alerts collection (notification-service)
{
  _id: ObjectId,
  user_id: "uuid",
  symbol: "BTCUSDT",
  condition: "ABOVE" | "BELOW",
  target_price: 70000.00,
  triggered: false,
  created_at: ISODate
}
```

**Audit rule:** Audit logs are append-only. Store in MongoDB `order_audit_log`. Never UPDATE — always INSERT with full state snapshot.

### 6.3 Redis — Cache, Sessions, Leaderboard

| Key Pattern | Value Type | TTL | Owner | Notes |
|---|---|---|---|---|
| `price:{SYMBOL}` | String | 30s | market-data-service | e.g. `price:BTCUSDT` |
| `rate_limit:{user_id}` | String | 60s | api-gateway | increment per request |
| `blacklist:{jti}` | String | token remaining | auth-service | TTL = token remaining life |
| `ws_session:{session_id}` | String | — | market-data-service | value = user_id |
| `leaderboard` | ZSet | — | portfolio-service | score = portfolio USD value |
| `portfolio_value:{user_id}` | String | 60s | portfolio-service | USD string decimal |

```
# Example Redis operations
SET price:BTCUSDT "67450.30" EX 30
INCR rate_limit:user-uuid-1
SET blacklist:jti-uuid "1" EXAT <unix-ts>
ZADD leaderboard 145230.50 "user-uuid-1"
ZREVRANGE leaderboard 0 99 WITHSCORES   -- top 100 leaderboard
```

**Kafka offsets:** Stored in Kafka itself (consumer groups), never in application DB.

---

## 7. Security Model

### Auth Flow

```
1. User → POST /api/v1/auth/login
2. auth-service validates credentials → issues JWT pair (RS256)
   - Access token:  15 min expiry
   - Refresh token: 7 days, stored hashed in PostgreSQL
3. Client sends: Authorization: Bearer <access_token>
4. API Gateway validates JWT signature using auth-service JWKS endpoint
   GET /.well-known/jwks.json → public key (kid-matched)
5. Gateway forwards X-User-Id header to downstream services
6. Services trust Gateway's validation — no re-validation
```

### JWT Structure

```json
{
  "sub": "user-uuid",           // user_id
  "jti": "token-uuid",          // for blacklist on logout
  "roles": ["USER"],            // ["USER"] | ["ADMIN"] | ["TRADER"]
  "iat": 1700000000,
  "exp": 1700000900,
  "kid": "key-id"               // matched against JWKS
}
```

### Security Controls

| Concern | Implementation |
|---|---|
| **Authentication** | JWT RS256, 15min access + 7day refresh |
| **Authorization** | Spring Security `@PreAuthorize`, roles: USER / ADMIN / TRADER |
| **OAuth2** | Google OAuth2 via Spring Security OAuth2 Client |
| **2FA** | TOTP (RFC 6238), `java-otp` library, QR code via ZXing |
| **Password Storage** | BCrypt strength 12 |
| **Rate Limiting** | Redis token bucket in API Gateway (60 req/min/user, 200 req/min/IP) |
| **SQL Injection** | JPA/Hibernate parameterized queries only |
| **Secrets** | AWS Secrets Manager + IRSA — never in env vars or code |
| **Transport** | TLS 1.3 only, HSTS header |
| **Input Validation** | Jakarta Bean Validation (`@Valid`) on all request DTOs |
| **Audit Logging** | All order events written to MongoDB audit log (append-only) |
| **CORS** | Strict origin allowlist in Gateway |
| **Dependency Scanning** | OWASP Dependency Check in CI |

### TOTP 2FA

- QR code generated with ZXing
- TOTP secret encrypted at rest (AES-256)
- Stored in `users.totp_secret` column (PostgreSQL)

### WebSocket Auth

- JWT in `Authorization: Bearer <token>` header on STOMP `CONNECT`
- Validated by `JwtConnectChannelInterceptor` before subscribing to topics

---

## 8. AWS Infrastructure

### Services Used

| Service | Purpose |
|---|---|
| **EKS** | Kubernetes cluster for all microservices (managed node groups) |
| **ECR** | Private Docker image registry |
| **RDS (PostgreSQL 15)** | Multi-AZ, automated backups, 3 separate databases (auth, trading, portfolio) |
| **ElastiCache (Redis 7)** | Cluster mode, 2 shards, read replicas |
| **MSK (Kafka 3.6)** | Managed Kafka, 3 brokers, multi-AZ |
| **S3** | PDF reports, static assets, CloudTrail logs |
| **Lambda** | Async report generation (triggered by SQS), scheduled leaderboard snapshots |
| **SQS** | Decouples Lambda report generation from reporting-service |
| **SES** | Transactional emails (order fills, price alerts, registration) |
| **ALB** | Load balancer for EKS ingress controller |
| **CloudWatch** | Centralized logs (Fluent Bit → CloudWatch), metrics, dashboards, alarms |
| **Secrets Manager** | DB passwords, API keys, JWT secret, Kafka credentials |
| **IAM / IRSA** | Service accounts via IAM Roles for Service Accounts |
| **VPC** | Private subnets for data stores, public subnet for ALB only |
| **Route 53** | DNS for `api.tradepulse.dev` |
| **ACM** | TLS certificate for domain |

### Infrastructure as Code

- **Tool:** Terraform — modules: `vpc`, `eks`, `rds`, `elasticache`, `msk`, `s3`, `lambda`
- **State:** S3 backend + DynamoDB lock table
- **Manifests root:** `infrastructure/terraform/`

### Kubernetes

- Each service: `Deployment`, `Service`, `HorizontalPodAutoscaler`, `ConfigMap`, `ServiceAccount`
- Shared: `Ingress` (AWS ALB Ingress Controller), `NetworkPolicy`, `PodDisruptionBudget`
- Secrets: External Secrets Operator (pulls from AWS Secrets Manager)
- **Manifests root:** `infrastructure/k8s/` (base + overlays via Kustomize)

---

## 9. Architecture Rules

### Service Boundaries

- Each service **owns its database exclusively**. No cross-service DB queries.
- Cross-service communication: **Kafka events** (async) or **REST** (sync, internal).
- Never reach into another service's repository from a different service.

### Layering

```
Controller → Service → Repository
```

- `@Transactional` at **service layer only** — never in controllers or repositories.
- No business logic in controllers; no DB logic in services beyond repository calls.

### Reactive vs Servlet

| Service | Runtime | Reason |
|---|---|---|
| `api-gateway-service` | **WebFlux** (Netty) | Reactive routing, high concurrency |
| All other services | **Servlet** (Tomcat) | Standard Spring Boot web |

### matching-engine Constraints

- **Stateless:** OrderBook is rebuilt from Kafka `order-events` on every startup
- **No HTTP business logic** (health endpoint only)
- **No `common-security` dependency** (internal service, no JWT needed)
- **<100ms p99 latency SLA** — zero DB calls in the hot matching path

### Database Assignment

| Database | Services |
|---|---|
| PostgreSQL | auth-service, user-service, order-service, portfolio-service, reporting-service |
| MongoDB | market-data-service (ticks), order-service (audit log), notification-service |
| Redis | **All services** (cache, rate limiting, WebSocket sessions) |

### Java Conventions

> **⚠️ Added beyond plan:** Moved from CLAUDE.md to Architecture.md for discoverability — important context for developers reading this doc.

**Naming:**
- DTO suffix: `PlaceOrderRequest`, `OrderResponse`
- Event suffix: `OrderFilledEvent`, `MarketDataEvent`
- Repository: `{Entity}Repository`
- Service impl: `{Service}ServiceImpl`
- Test: `{Class}Test`, `{Class}IntegrationTest`

**Injection:** Constructor injection with `@RequiredArgsConstructor`. Never `@Autowired` field injection.

**Exceptions:** Domain exceptions extend `RuntimeException`. Global handler via `@RestControllerAdvice`. Never throw raw `RuntimeException` or return null.

**Logging:** `@Slf4j` from Lombok. MDC for correlation IDs. Structured: `log.info("Order placed: orderId={}, symbol={}", ...)`. Never `System.out.println()`.

**Testing:** Testcontainers for integration (PostgreSQL, Kafka, MongoDB). Mockito for unit test collaborators. WireMock for Binance mock.

---

## 10. Hard Rules — Never Do These

- ❌ Never use `@Transactional` at controller or repository level
- ❌ Never call one service's repository from another service
- ❌ Never store secrets in code or `application.yml`
- ❌ Never use `System.out.println` (use `@Slf4j`)
- ❌ Never use `@Autowired` field injection
- ❌ Never hard-code portfolio value calculation (always read current Redis price)
- ❌ Never persist OrderBook to database (rebuilt from Kafka)
- ❌ Never UPDATE audit logs (append-only)
- ❌ Never use `double`/`float` for monetary values
- ❌ Never use random Kafka message keys
- ❌ Never skip idempotency checks in Kafka consumers
- ❌ Never query PostgreSQL for real-time price (use Redis)
- ❌ Never add database calls in matching-engine hot path

---

## 11. Tech Stack Summary

| Category | Technology | Version |
|---|---|---|
| Language | Java (Virtual Threads) | 21 |
| Framework | Spring Boot | 3.2.5 |
| Cloud Gateway | Spring Cloud | 2023.0.1 |
| Messaging | Apache Kafka | 3.6 |
| Relational DB | PostgreSQL | 15 |
| Document DB | MongoDB | 7 |
| Cache | Redis | 7 |
| Auth | JJWT (RS256) | 0.12.5 |
| PDF | iText7 | 7.2.5 |
| Mapper | MapStruct | 1.5.5 |
| Testing | Testcontainers | 1.19.x |
| Build | Maven (multi-module) | — |
| Container | Docker + Kubernetes (EKS) | — |
| IaC | Terraform | — |
| CI/CD | GitHub Actions | — |
| Monitoring | CloudWatch + Actuator + Micrometer | — |

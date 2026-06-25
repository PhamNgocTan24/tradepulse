# TradePulse

Crypto trading platform using real-time Binance WebSocket streams. Built with Java 21 Spring Boot microservices, event-driven architecture via Kafka, and polyglot persistence (PostgreSQL/MongoDB/Redis). Currently in scaffold phase with no source files yet.

---

## Commands

### Build
```bash
mvn clean install -DskipTests          # full build, skip tests
mvn clean install                      # full build with tests
```

### Run locally
```bash
cd docker && docker-compose up -d      # starts Postgres, Mongo, Redis, Kafka, Zookeeper
mvn spring-boot:run -pl services/auth-service
```

### Test
```bash
mvn test -pl services/auth-service     # single service
mvn test                               # all services
mvn test -pl services/order-service -Dtest=OrderServiceTest  # single test class
```

---

## Tech Stack

| Layer        | Technology           | Version   |
|--------------|---------------------|-----------|
| Language     | Java                | 21        |
| Framework    | Spring Boot         | 3.2.5     |
| Spring Cloud | Spring Cloud        | 2023.0.1  |
| Messaging    | Apache Kafka        | 3.6       |
| Primary DB   | PostgreSQL          | 15        |
| Document DB  | MongoDB             | 7         |
| Cache        | Redis               | 7         |
| Auth         | JJWT (RS256)        | 0.12.5    |
| PDF          | iText7              | 7.2.5     |
| Mapper       | MapStruct           | 1.5.5     |
| Testing      | Testcontainers      | 1.19.x    |

---

## Coordinates

**Root group:** `com.tradepulse`

**Services:** `com.tradepulse.{service}`
- `api-gateway` → `com.tradepulse.gateway`
- `auth-service` → `com.tradepulse.auth`
- `order-service` → `com.tradepulse.order`
- `matching-engine` → `com.tradepulse.matching`
- `market-data-service` → `com.tradepulse.marketdata`
- `portfolio-service` → `com.tradepulse.portfolio`
- `user-service` → `com.tradepulse.user`
- `notification-service` → `com.tradepulse.notification`
- `reporting-service` → `com.tradepulse.reporting`

**Shared modules:**
- `common-dto` → `com.tradepulse.common.dto`
- `common-security` → `com.tradepulse.security`

---

## Architecture Rules

**Service boundaries:** Each service owns its database. No cross-service DB queries. Use Kafka events or REST only.

**Layering:** Controller → Service → Repository. `@Transactional` at service layer only.

**Reactive vs Servlet:**
- `api-gateway`: WebFlux (reactive, Netty)
- All other services: Servlet (spring-boot-starter-web, Tomcat)

**market-data-service dual transport:**
- WebFlux WebClient for Binance WebSocket client (inbound)
- Spring WebSocket STOMP for client-facing WebSocket (outbound)

**matching-engine is special:**
- Stateless (OrderBook rebuilt from Kafka on startup)
- No HTTP business logic (health endpoint only)
- No `common-security` dependency (internal service)
- <100ms p99 latency SLA (no DB in hot path)

**Database assignment:**
- PostgreSQL: auth-service, user-service, order-service, portfolio-service, reporting-service
- MongoDB: market-data-service (ticks), order-service (audit log), notification-service
- Redis: All services (cache, rate limiting, WebSocket sessions)

---

## Kafka Topics

| Topic            | Key       | Partitions | Producers            | Consumers                          |
|------------------|-----------|------------|----------------------|------------------------------------|
| market-data      | symbol    | 10         | market-data-service  | matching-engine, notification      |
| order-events     | order_id  | 5          | order-service, matching-engine | order-service, portfolio-service |
| portfolio-events | user_id   | 5          | matching-engine      | portfolio-service                  |
| notifications    | user_id   | 3          | notification-service | notification-service               |

**Event Types in order-events:**

| event_type       | Direction     | Description                      |
|------------------|---------------|----------------------------------|
| NEW_ORDER        | → engine      | Triggers matching                |
| ORDER_FILLED     | ← engine      | Full fill complete               |
| PARTIAL_FILL     | ← engine      | Partial quantity matched         |
| ORDER_CANCELLED  | bidirectional | User cancel or engine cancel     |

---

## Redis Keys

| Key Pattern                   | Value Type  | TTL   | Owner                | Notes                      |
|-------------------------------|-------------|-------|----------------------|----------------------------|
| price:{SYMBOL}                | String      | 30s   | market-data-service  | e.g. price:BTCUSDT         |
| rate_limit:{user_id}          | String      | 60s   | api-gateway          | increment per request      |
| blacklist:{jti}               | String      | token | auth-service         | TTL = token remaining life |
| ws_session:{session_id}       | String      | —     | market-data-service  | value = user_id            |
| leaderboard                   | ZSet        | —     | portfolio-service    | score = portfolio USD value|
| portfolio_value:{user_id}     | String      | 60s   | portfolio-service    | USD string decimal         |

---

## Database Rules

**Decimal precision:** All monetary values: `DECIMAL(18, 8)` in SQL, `BigDecimal` in Java. Never `double` or `float`.

**Audit logs:** Append-only. Store in MongoDB `order_audit_log` collection. Never UPDATE, always INSERT with full state snapshot.

**Kafka offsets:** Stored in Kafka itself (consumer groups), not in application database.

**Connection pooling:** HikariCP (Spring Boot default). Configure per service based on load.

---

## Security Rules

**Auth flow:**
1. User authenticates → auth-service issues JWT (RS256, 15min access + 7day refresh)
2. Gateway validates JWT signature with public key, forwards to services
3. Services trust Gateway's validation (no re-validation)

**JWT structure:**
- `sub`: user_id (UUID)
- `jti`: token ID (for blacklist on logout)
- `roles`: ["USER"], ["ADMIN"], ["TRADER"]

**TOTP 2FA:**
- QR code generated with ZXing
- TOTP secret encrypted at rest (AES-256)
- Stored in `users.totp_secret` column (PostgreSQL)

**Secrets:**
- All secrets from AWS Secrets Manager (IRSA for authentication)
- Never hardcode passwords, API keys, connection strings
- Never commit `.env` files

**WebSocket auth:**
- JWT in `Authorization: Bearer <token>` header on STOMP CONNECT
- Validated before subscribing to topics

---

## Java Conventions

**Naming:**
- DTO suffix: `PlaceOrderRequest`, `OrderResponse`
- Event suffix: `OrderFilledEvent`, `MarketDataEvent`
- Repository: `{Entity}Repository`
- Service impl: `{Service}ServiceImpl`
- Test: `{Class}Test`, `{Class}IntegrationTest`

**Injection:**
- Constructor injection with `@RequiredArgsConstructor`
- Never `@Autowired` field injection

**Exceptions:**
- Domain exceptions extend `RuntimeException`
- Global handler: `@RestControllerAdvice`
- Never throw raw `RuntimeException` or return null

**Logging:**
- `@Slf4j` from Lombok
- MDC for correlation IDs
- Structured: `log.info("Order placed: orderId={}, symbol={}", ...)`
- Never `System.out.println()` or `printStackTrace()`

**Testing:**
- Integration tests: Testcontainers (PostgreSQL, Kafka, MongoDB)
- Unit tests: Mockito for collaborators
- Mock external APIs only (WireMock for Binance)

---

## Binance WebSocket

**Endpoint:** `wss://stream.binance.com:9443`

**Streams:**
- `btcusdt@ticker`
- `ethusdt@ticker`
- `solusdt@ticker`

**Flow:**
```
Binance WebSocket
  ↓
market-data-service (websocket/BinanceWebSocketClient.java)
  ↓ normalize to MarketTick domain object
  ├── MongoDB: persist to market_ticks collection
  ├── Redis: SET price:{SYMBOL} (TTL 30s)
  ├── Kafka: publish to market-data topic (partitioned by symbol)
  └── STOMP: push to /topic/prices WebSocket subscribers
```

**Reconnection logic:**
- Exponential backoff: 1s, 2s, 4s, 8s, max 30s
- Circuit breaker: Resilience4j with 5 consecutive failures threshold
- Health indicator: Mark service unhealthy if disconnected >60s

---

## Never Do These

- ❌ Never use `@Transactional` at controller or repository level
- ❌ Never call one service's repository from another service
- ❌ Never store secrets in code or application.yml
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

## Reference Links

**Structured docs (`docs/`):**
- [Architecture](docs/architecture/Architecture.md) — system design, service catalogue, Kafka flows, DB schema, security
- [Design Patterns](docs/development/Patterns.md) — 15 distributed-systems patterns
- [Syntax Guide](docs/development/Syntax.md) — 15 Java bad/good examples
- [Folder Structure](docs/development/FolderStructure.md) — package and folder conventions
- [API Contracts](docs/api/ApiContracts.md) — REST contracts, error codes, rate limiting
- [Task & Roadmap](docs/planning/Task.md) — FR/NFR requirements, implementation roadmap
- [Runbook](docs/operations/Runbook.md) — local dev setup, start/stop services, troubleshooting

**Root copies (kept for backward compatibility):**
- [SYNTAX.md](SYNTAX.md) · [DESIGN_PATTERNS.md](DESIGN_PATTERNS.md) · [API_CONTRACTS.md](API_CONTRACTS.md) · [FOLDER_STRUCTURE.md](FOLDER_STRUCTURE.md)

**Original spec:**
- [Architecture spec](.kiro/specs/tradepulse.md)

**Infrastructure:**
- Terraform modules: `infrastructure/terraform/modules/`
- Kubernetes manifests: `infrastructure/k8s/`

**Dependencies:**
- [Spring Boot 3.2.5](https://docs.spring.io/spring-boot/docs/3.2.5/reference/html/)
- [Spring Cloud 2023.0.1](https://docs.spring.io/spring-cloud/docs/2023.0.1/reference/html/)
- [Kafka 3.6](https://kafka.apache.org/36/documentation.html)
- [Binance WebSocket](https://binance-docs.github.io/apidocs/spot/en/#websocket-market-streams)

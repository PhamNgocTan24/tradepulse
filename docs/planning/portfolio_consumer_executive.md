# EXECUTIVE DESIGN PLAN: TASK-6 & TASK-7 — Portfolio Consumer + Real-Time Value Updates

> **Branch Name:** `task/portfolio-consumer-holdings`
> **Target Services:** `portfolio-service`
> **Status:** ⬜ Draft | ⬜ Approved by Tech Lead | ⬜ In Progress | ⬜ Completed

---

## 1. Context & Architectural Guardrails

- [x] **Data Types:** All balance, price, and quantity variables MUST use Java `BigDecimal` and SQL `DECIMAL(18, 8)`. NO double/float.
- [x] **Dependency Injection:** Use Constructor Injection via Lombok `@RequiredArgsConstructor`. NO field-level `@Autowired`.
- [x] **Database Boundary:** No cross-service DB queries. Portfolio-service reads live prices from Redis only (`price:{SYMBOL}`, TTL 30s). It never queries order-service's PostgreSQL.
- [x] **Idempotency:** Per Pattern #3 — every Kafka consumer MUST deduplicate by a stable `eventId` before applying side effects.
- [x] **Optimistic Locking:** Per Pattern #12 — `Holding` and `PortfolioAccount` entities MUST carry `@Version` to handle concurrent fill events safely.

### Gaps Found in Current Code

| Issue | Current State | Required Fix |
|---|---|---|
| No idempotency guard | `PortfolioEventConsumer` calls `applyFill()` blindly | Add `EventLog` entity + check in consumer |
| No `@Version` on entities | `Holding`, `PortfolioAccount` lack `@Version` field | Add `@Version long version` + `@Retryable` |
| Leaderboard only updated on GET | `applyFill()` never writes Redis leaderboard | Write `portfolio_value:{userId}` + `ZADD leaderboard` after every fill |
| `applyFill()` uses `orElse()` | Creates phantom accounts silently on every call | Change to `orElseThrow(PortfolioAccountNotFoundException::new)` |
| Realized P&L not computed on SELL | `Transaction.realizedPnl` is `null` on SELL | Compute from avg cost basis |
| No DLQ routing | Consumer has no exception handling | Route failures to `portfolio-events-dlq` via `KafkaTemplate` |

---

## 2. Infrastructure Changes

### 2.1. Database Schema Updates (PostgreSQL)

**Service:** `portfolio-service` | **DB:** `tradepulse_portfolio`

#### New Table: `event_log`
```sql
CREATE TABLE event_log (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id     UUID NOT NULL,
    processed_at TIMESTAMP DEFAULT NOW(),
    CONSTRAINT uk_event_log_event_id UNIQUE (event_id)
);
```

#### Modify: `holdings` — add optimistic locking
```sql
ALTER TABLE holdings ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
```

#### Modify: `portfolio_accounts` — add optimistic locking
```sql
ALTER TABLE portfolio_accounts ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
```

> `ddl-auto: update` will apply these automatically in the dev environment. A Flyway migration must be written for production.

### 2.2. Kafka Integration

| Topic | Group ID | Events consumed | Key |
|---|---|---|---|
| `portfolio-events` | `portfolio-service` | `ORDER_FILLED`, `PARTIAL_FILL` | `user_id` |
| `portfolio-events-dlq` | — | (failures routed here) | `user_id` |

No new topic partitioning changes are required. The DLQ topic should be created manually or via admin config.

### 2.3. Redis Caching

After each successful `applyFill()`:

| Key Pattern | Value Type | TTL | Operation |
|---|---|---|---|
| `portfolio_value:{userId}` | String | 60s | `SET portfolio_value:{userId} <total> EX 60` |
| `leaderboard` | ZSet | none | `ZADD leaderboard <totalValue> <userId>` |

Live prices are **read** from `price:{SYMBOL}` (TTL 30s, written by `market-data-service`). **Never query PostgreSQL for prices.**

---

## 3. Step-by-Step Coding Plan

### 3.1. New: EventLog Entity + Repository

#### 📂 Package: `com.tradepulse.portfolio.domain.entity`
- **[NEW] `EventLog.java`**
  * JPA `@Entity`, table `event_log`
  * Fields: `id` (UUID, PK, `@GeneratedValue`), `eventId` (UUID, `@Column(unique = true)`), `processedAt` (Instant, `@CreationTimestamp`)
  * Lombok: `@Getter @NoArgsConstructor @AllArgsConstructor @Builder`

#### 📂 Package: `com.tradepulse.portfolio.repository`
- **[NEW] `EventLogRepository.java`**
  * Extends `JpaRepository<EventLog, UUID>`
  * Method: `boolean existsByEventId(UUID eventId);`

---

### 3.2. Modify: Holding & PortfolioAccount — Add @Version

#### 📂 Package: `com.tradepulse.portfolio.domain.entity`
- **[MODIFY] `Holding.java`** — Add field:
  ```java
  @Version
  private long version;
  ```

- **[MODIFY] `PortfolioAccount.java`** — Add field:
  ```java
  @Version
  private long version;
  ```

---

### 3.3. Modify: PortfolioEventConsumer — Idempotency Guard + DLQ

#### 📂 Package: `com.tradepulse.portfolio.event.consumer`
- **[MODIFY] `PortfolioEventConsumer.java`**
  * Inject: `EventLogRepository eventLogRepository`, `KafkaTemplate<String, PortfolioEvent> kafkaTemplate`
  * Add `@Transactional` to the listener method
  * Logic:
    1. If `eventLogRepository.existsByEventId(event.eventId())` → log skip and `return`
    2. Otherwise call `portfolioService.applyFill(event)`
    3. Save `EventLog.builder().eventId(event.eventId()).build()`
    4. On any exception: `kafkaTemplate.send("portfolio-events-dlq", event.userId().toString(), event)` + log error

```java
@KafkaListener(topics = "portfolio-events", groupId = "portfolio-service",
               containerFactory = "kafkaListenerContainerFactory")
@Transactional
public void onPortfolioEvent(PortfolioEvent event) {
    if (eventLogRepository.existsByEventId(event.eventId())) {
        log.info("Duplicate event skipped: eventId={}", event.eventId());
        return;
    }
    try {
        portfolioService.applyFill(event);
        eventLogRepository.save(EventLog.builder().eventId(event.eventId()).build());
    } catch (Exception ex) {
        kafkaTemplate.send("portfolio-events-dlq", event.userId().toString(), event);
        log.error("Portfolio fill failed, sent to DLQ: eventId={}, error={}", event.eventId(), ex.getMessage(), ex);
    }
}
```

---

### 3.4. Modify: PortfolioServiceImpl — Fix applyFill + Redis + P&L

#### 📂 Package: `com.tradepulse.portfolio.service.impl`
- **[MODIFY] `PortfolioServiceImpl.java`**

**a) Add `@Retryable` for optimistic lock conflicts:**
```java
@Retryable(retryFor = OptimisticLockException.class, maxAttempts = 3,
           backoff = @Backoff(delay = 100))
@Override
@Transactional
public void applyFill(PortfolioEvent event) { ... }
```

**b) Fix phantom account creation:**
```java
// Before (BAD):
PortfolioAccount account = accountRepository.findById(userId)
        .orElse(PortfolioAccount.builder().userId(userId).build());

// After (GOOD):
PortfolioAccount account = accountRepository.findById(userId)
        .orElseThrow(() -> new PortfolioAccountNotFoundException(userId));
```

**c) Add realized P&L computation for SELL fills:**
```java
// Inside the SELL branch, after computing tradeValue:
BigDecimal realizedPnl = null;
holdingRepository.findByUserIdAndSymbol(userId, symbol).ifPresent(h -> {
    realizedPnl = tradeValue.subtract(
            h.getAvgCostBasis().multiply(qty).setScale(8, RoundingMode.HALF_UP));
    h.setQuantity(h.getQuantity().subtract(qty).max(BigDecimal.ZERO));
    holdingRepository.save(h);
});
// Pass realizedPnl to Transaction.builder()
```

**d) Add Redis portfolio value + leaderboard update after every fill:**
```java
// After accountRepository.save(account):
updateRedisPortfolioValue(userId, account.getCashBalance());
```

**e) Add private helper `updateRedisPortfolioValue`:**
```java
private void updateRedisPortfolioValue(UUID userId, BigDecimal cashBalance) {
    List<Holding> holdings = holdingRepository.findByUserId(userId);
    BigDecimal holdingsValue = holdings.stream()
        .filter(h -> h.getQuantity().compareTo(BigDecimal.ZERO) > 0)
        .map(h -> getLivePrice(h.getSymbol()).multiply(h.getQuantity()))
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .setScale(8, RoundingMode.HALF_UP);
    BigDecimal totalValue = cashBalance.add(holdingsValue).setScale(8, RoundingMode.HALF_UP);

    redisTemplate.opsForValue().set(
        PORTFOLIO_PREFIX + userId, totalValue.toPlainString(), Duration.ofSeconds(60));
    redisTemplate.opsForZSet().add(LEADERBOARD_KEY, userId.toString(), totalValue.doubleValue());

    log.info("Redis portfolio updated: userId={}, totalValue={}", userId, totalValue);
}
```

---

### 3.5. New: PortfolioAccountNotFoundException

#### 📂 Package: `com.tradepulse.portfolio.exception`
- **[NEW] `PortfolioAccountNotFoundException.java`**
  ```java
  public class PortfolioAccountNotFoundException extends RuntimeException {
      public PortfolioAccountNotFoundException(UUID userId) {
          super("Portfolio account not found for userId: " + userId);
      }
  }
  ```

---

### 3.6. New: KafkaProducerConfig for DLQ publishing

#### 📂 Package: `com.tradepulse.portfolio.config`
- **[NEW] `KafkaProducerConfig.java`**
  * `@Configuration` class
  * Declares `ProducerFactory<String, PortfolioEvent>` bean using `JsonSerializer`
  * Declares `KafkaTemplate<String, PortfolioEvent>` bean
  * Reads `spring.kafka.bootstrap-servers` from `application.yml`

---

## 4. Definition of Done & Acceptance Criteria

- **AC-1:** Duplicate `eventId` in `portfolio-events` is silently skipped — `event_log` table prevents re-application.
- **AC-2:** After a `BUY` fill: holding quantity increases, `avgCostBasis` is recalculated as weighted average, `cashBalance` decreases by `qty × fillPrice`.
- **AC-3:** After a `SELL` fill: holding quantity decreases, `cashBalance` increases by `qty × fillPrice`, `Transaction.realizedPnl` is calculated and persisted.
- **AC-4:** After every fill, Redis keys `portfolio_value:{userId}` (TTL 60s) and `leaderboard` ZSet are updated.
- **AC-5:** Concurrent fills for the same user do not cause data corruption — `@Version` triggers `OptimisticLockException`, which is retried up to 3 times.
- **AC-6:** A failed fill event (e.g., missing account) is routed to `portfolio-events-dlq` — consumer does not block.
- **AC-7:** `mvn test -pl services/portfolio-service` passes.

---

## 5. Verification Plan

### 5.1. Automated Unit / Integration Tests

| Test Class | Type | Key Scenarios |
|---|---|---|
| `PortfolioServiceImplTest` | Unit (Mockito) | BUY fill: qty increases, cash decreases, avg cost recalculated. SELL fill: qty decreases, cash increases, P&L computed. Redis writes called. |
| `PortfolioEventConsumerTest` | Unit (Mockito) | Duplicate `eventId` → skipped. Exception → DLQ route. |
| `PortfolioServiceIntegrationTest` | Integration (Testcontainers) | Full E2E: Kafka → consumer → PostgreSQL + Redis |

```
Command: mvn test -pl services/portfolio-service
```

### 5.2. Manual Verification Steps

```bash
# 1. Start infrastructure
cd docker && docker-compose up -d

# 2. Start portfolio-service
mvn spring-boot:run -pl services/portfolio-service

# 3. Publish a test fill event to Kafka (replace UUIDs)
kafka-console-producer --bootstrap-server localhost:9092 \
  --topic portfolio-events \
  --property "parse.key=true" --property "key.separator=:"

# 4. Verify PostgreSQL holdings table
psql -U tradepulse -d tradepulse_portfolio \
  -c "SELECT * FROM holdings WHERE user_id = '<user-uuid>';"

# 5. Verify Redis
redis-cli GET portfolio_value:<user-uuid>
redis-cli ZSCORE leaderboard <user-uuid>

# 6. Re-publish same eventId → verify holdings NOT double-counted (idempotency)

# 7. Call the REST API
curl -H "Authorization: Bearer <token>" http://localhost:8085/api/portfolio/me
```

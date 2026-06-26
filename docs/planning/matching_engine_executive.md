# EXECUTIVE DESIGN PLAN: Implement memory-based OrderBook matching in matching-engine

> **Branch Name:** `task/matching-engine-impl`
> **Target Services:** `matching-engine`
> **Status:** ✅ Completed

---

## 1. Context & Architectural Guardrails
- [x] **Data Types:** All balance, price, and quantity variables MUST use Java `BigDecimal` and SQL `DECIMAL(18, 8)`. NO double/float.
- [x] **Dependency Injection:** Use Constructor Injection via Lombok `@RequiredArgsConstructor`. NO field-level `@Autowired`.
- [x] **Database Boundary:** No cross-database/cross-service queries or direct repository calls.
- [x] **Stateless Rules (If matching-engine):** No database calls in hot path.

---

## 2. Infrastructure Changes

### 2.1. Database Schema updates (PostgreSQL / MongoDB)
None. The matching-engine is completely stateless and does not call any database.

### 2.2. Kafka Integration
None. The existing Kafka integration consumes from `order-events` and `market-data`, and produces to `order-events` and `portfolio-events`.

### 2.3. Redis Caching
None.

---

## 3. Step-by-Step Coding Plan

### 3.1. matching-engine Changes

#### 📂 Package: `com.tradepulse.matching.engine`

- **[MODIFY] [MatchingResult.java](../../services/matching-engine/src/main/java/com/tradepulse/matching/engine/MatchingResult.java)**:
  * Add `String side` field to the record to allow downstream result publishers to know the order side (BUY/SELL) without having the original `OrderEvent`.

- **[MODIFY] [OrderBook.java](../../services/matching-engine/src/main/java/com/tradepulse/matching/engine/OrderBook.java)**:
  * Update `match` call to include `incoming.side()` when returning `MatchingResult`.
  * Add a new method `public List<MatchingResult> matchAgainstPrice(BigDecimal currentPrice)` to evaluate resting bids and asks against the new market price and return all executed fills.

#### 📂 Package: `com.tradepulse.matching.service`

- **[MODIFY] [MatchingService.java](../../services/matching-engine/src/main/java/com/tradepulse/matching/service/MatchingService.java)**:
  * Implement `onPriceUpdate(String symbol, BigDecimal newPrice)`: retrieve the symbol's order book, synchronize on it, invoke `matchAgainstPrice(newPrice)`, and publish the results to Kafka.

#### 📂 Package: `com.tradepulse.matching.event.producer`

- **[MODIFY] [OrderResultProducer.java](../../services/matching-engine/src/main/java/com/tradepulse/matching/event/producer/OrderResultProducer.java)**:
  * Update `publishResult` call to accommodate the new `side` field in `MatchingResult`.
  * Add a new method `public void publishLimitOrderFill(MatchingResult result)` to publish fills triggered by market price updates.

#### 📂 Package: `com.tradepulse.matching`

- **[NEW] `OrderBookTest.java`**:
  * Unit tests verifying `OrderBook` matching logic: limit matching, market matching, and price updates triggering limit order execution.

- **[NEW] `MatchingServiceTest.java`**:
  * Unit tests verifying `MatchingService` orchestrates events properly.

---

## 4. Definition of Done & Acceptance Criteria
- **AC-1:** Market data updates proactively trigger match evaluations for resting limit orders.
- **AC-2:** Fully matched limit orders are correctly published to both `order-events` and `portfolio-events` topics.
- **AC-3:** Zero database calls are executed in the matching path.
- **AC-4:** Compilation and unit tests pass.

---

## 5. Verification Plan

### 5.1. Automated Unit / Integration Tests
- Command: `mvn test -pl services/matching-engine`

### 5.2. Manual Verification
None required if all unit tests verify the DSA state transitions perfectly.

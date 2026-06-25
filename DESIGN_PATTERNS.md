# TradePulse Design Patterns

Architecture and distributed-systems patterns for TradePulse. For implementation-level examples, see [SYNTAX.md](SYNTAX.md). For compact project context, see [CLAUDE.md](CLAUDE.md).

---

#### **1. Transactional Outbox Pattern - Atomic DB + Kafka**

**❌ BAD:**
```java
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    @Transactional
    public Order place(PlaceOrderRequest request) {
        Order order = orderRepository.save(Order.from(request));
        kafkaTemplate.send("order-events", order.getId().toString(), OrderPlacedEvent.from(order));
        return order;
    }
}
```

**✅ GOOD:**
```java
@Entity
public class OutboxEvent {
    @Id
    private UUID id;
    private String topic;
    private String messageKey;
    @Lob
    private String payload;
    private boolean published;
    private Instant createdAt;
}

@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;

    @Transactional
    public Order place(PlaceOrderRequest request) {
        Order order = orderRepository.save(Order.from(request));
        outboxEventRepository.save(OutboxEventFactory.orderPlaced(order));
        return order;
    }
}

@Component
@RequiredArgsConstructor
public class OutboxPublisher {
    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPendingEvents() {
        for (OutboxEvent event : outboxEventRepository.findTop100ByPublishedFalseOrderByCreatedAtAsc()) {
            kafkaTemplate.send(event.getTopic(), event.getMessageKey(), event.getPayload());
            event.markPublished();
        }
    }
}
```

**Rule:** Persist the business write and the Kafka intent in one transaction, then publish asynchronously.

#### **2. Cache-Aside Pattern - Redis for Real-Time Prices**

**❌ BAD:**
```java
@Service
@RequiredArgsConstructor
public class PriceService {
    private final PriceRepository priceRepository;

    public BigDecimal currentPrice(String symbol) {
        return priceRepository.findLatest(symbol)
                .orElseThrow()
                .getPrice();
    }
}
```

**✅ GOOD:**
```java
@Service
@RequiredArgsConstructor
public class PriceService {
    private final StringRedisTemplate redisTemplate;

    public BigDecimal currentPrice(String symbol) {
        String key = "price:" + symbol.toUpperCase();
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            throw new PriceNotAvailableException(symbol);
        }
        return new BigDecimal(value);
    }
}
```

**Rule:** Use Redis as the fast read path for live prices; treat a cache miss as a source-of-truth gap.

#### **3. Idempotency Guard - Kafka Consumer Deduplication**

**❌ BAD:**
```java
@KafkaListener(topics = "order-events")
public void consume(OrderFilledEvent event) {
    portfolioService.applyFill(event);
}
```

**✅ GOOD:**
```java
@Entity
@Table(name = "event_log", uniqueConstraints = @UniqueConstraint(name = "uk_event_id", columnNames = "eventId"))
public class EventLog {
    @Id
    private UUID id;
    private UUID eventId;
    private Instant processedAt;
}

@Service
@RequiredArgsConstructor
public class OrderEventConsumer {
    private final EventLogRepository eventLogRepository;
    private final PortfolioService portfolioService;

    @KafkaListener(topics = "order-events", groupId = "portfolio-service")
    @Transactional
    public void consume(OrderFilledEvent event) {
        if (eventLogRepository.existsByEventId(event.eventId())) {
            return;
        }
        portfolioService.applyFill(event);
        eventLogRepository.save(EventLog.processed(event.eventId()));
    }
}
```

**Rule:** Every consumer MUST deduplicate by a stable event identifier before applying side effects.

#### **4. Event-Driven Microservices - Kafka Over REST**

**❌ BAD:**
```java
@Service
@RequiredArgsConstructor
public class OrderService {
    private final RestTemplate restTemplate;

    public void handleFill(OrderFilledEvent event) {
        restTemplate.postForObject("http://portfolio-service/api/portfolio/fills", event, Void.class);
    }
}
```

**✅ GOOD:**
```java
@Service
@RequiredArgsConstructor
public class OrderService {
    private final KafkaTemplate<String, OrderFilledEvent> kafkaTemplate;

    public void publishFill(OrderFilledEvent event) {
        kafkaTemplate.send("order-events", event.orderId().toString(), event);
    }
}

@KafkaListener(topics = "order-events", groupId = "portfolio-service")
public void consume(OrderFilledEvent event) {
    portfolioService.applyFill(event);
}
```

**Rule:** Prefer Kafka events for inter-service state changes so services stay loosely coupled and replayable.

#### **5. Service Boundary Enforcement - No Cross-Service DB Access**

**❌ BAD:**
```java
@Service
@RequiredArgsConstructor
public class PortfolioService {
    private final JdbcTemplate orderJdbcTemplate;

    public List<OrderRow> recentOrders(UUID userId) {
        return orderJdbcTemplate.query(...);
    }
}
```

**✅ GOOD:**
```java
@Service
@RequiredArgsConstructor
public class PortfolioProjectionService {
    private final KafkaTemplate<String, PortfolioEvent> kafkaTemplate;

    public void emitPortfolioChange(PortfolioEvent event) {
        kafkaTemplate.send("portfolio-events", event.userId().toString(), event);
    }
}

@KafkaListener(topics = "order-events", groupId = "portfolio-service")
public void consume(OrderFilledEvent event) {
    portfolioProjectionRepository.save(PortfolioProjection.from(event));
}
```

**Rule:** Never reach into another service’s database; consume its events or call its API.

#### **6. Circuit Breaker Pattern - External API Resilience**

**❌ BAD:**
```java
@Component
public class BinanceClient {
    public String connect() {
        return webClient.get().uri("/ws").retrieve().bodyToMono(String.class).block();
    }
}
```

**✅ GOOD:**
```java
@Component
@RequiredArgsConstructor
public class BinanceClient {
    private final WebClient webClient;

    @CircuitBreaker(name = "binance", fallbackMethod = "fallback")
    @Retry(name = "binance")
    public Mono<String> connect() {
        return webClient.get().uri("/ws").retrieve().bodyToMono(String.class);
    }

    Mono<String> fallback(Throwable throwable) {
        return Mono.empty();
    }
}
```

**Rule:** Protect external integrations with timeout, retry, and circuit-breaker controls.

#### **7. API Gateway Centralization - Auth, Rate Limiting, Routing**

**❌ BAD:**
```java
@RestController
public class OrderController {
    @GetMapping("/api/orders")
    public List<OrderResponse> list(@RequestHeader("Authorization") String token) {
        return List.of();
    }
}
```

**✅ GOOD:**
```java
@Configuration
public class GatewayRoutes {
    @Bean
    RouteLocator routeLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("order-service", route -> route.path("/api/orders/**")
                        .filters(filter -> filter.filter(new JwtAuthenticationFilter()))
                        .uri("lb://order-service"))
                .build();
    }
}
```

**Rule:** Centralize gateway concerns such as auth, routing, and rate limiting in one edge service.

#### **8. Event Sourcing (Partial) - Matching Engine State Rebuild**

**❌ BAD:**
```java
@Service
public class MatchingEngine {
    private final MatchingStateRepository matchingStateRepository;

    public void match(Order order) {
        matchingStateRepository.save(orderBookSnapshot());
    }
}
```

**✅ GOOD:**
```java
@Component
public class OrderBookRebuilder {
    private final Map<String, OrderBook> books = new ConcurrentHashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    public void rebuild() {
        // Consume historical order-events and reconstruct state in memory.
    }
}
```

**Rule:** Rebuild the matching engine from Kafka history instead of persisting hot-path order-book state.

#### **9. CQRS Pattern - Write to PostgreSQL, Read from Redis**

**❌ BAD:**
```java
@Service
@RequiredArgsConstructor
public class PortfolioQueryService {
    private final PortfolioRepository portfolioRepository;

    public BigDecimal portfolioValue(UUID userId) {
        return portfolioRepository.calculateLiveValue(userId);
    }
}
```

**✅ GOOD:**
```java
@Service
@RequiredArgsConstructor
public class PortfolioCommandService {
    private final PortfolioRepository portfolioRepository;
    private final StringRedisTemplate redisTemplate;

    @Transactional
    public void applyFill(PortfolioEvent event) {
        portfolioRepository.save(Portfolio.from(event));
        redisTemplate.opsForValue().set("portfolio_value:" + event.userId(), event.totalValue().toPlainString(), Duration.ofSeconds(60));
    }
}
```

**Rule:** Keep writes in the relational model and expose fast reads through Redis projections.

#### **10. Saga Pattern - Order Placement Orchestration**

**❌ BAD:**
```java
@Transactional
public void placeOrder(PlaceOrderRequest request) {
    balanceService.reserve(request.userId(), request.notional());
    orderRepository.save(Order.from(request));
    portfolioService.update(request);
}
```

**✅ GOOD:**
```java
@Service
@RequiredArgsConstructor
public class OrderPlacementSaga {
    private final BalanceService balanceService;
    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    @Transactional
    public UUID start(PlaceOrderRequest request) {
        balanceService.reserve(request.userId(), request.notional());
        Order order = orderRepository.save(Order.from(request));
        kafkaTemplate.send("order-events", order.getId().toString(), OrderPlacedEvent.from(order));
        return order.getId();
    }
}
```

**Rule:** Model cross-service workflows as a saga with explicit compensation rather than a distributed transaction.

#### **11. Dead Letter Queue (DLQ) - Failed Message Handling**

**❌ BAD:**
```java
@KafkaListener(topics = "notifications")
public void consume(NotificationEvent event) {
    notificationService.send(event);
}
```

**✅ GOOD:**
```java
@KafkaListener(topics = "notifications", groupId = "notification-service")
public void consume(NotificationEvent event) {
    try {
        notificationService.send(event);
    } catch (Exception ex) {
        kafkaTemplate.send("notifications-dlq", event.userId().toString(), event);
    }
}
```

**Rule:** Catch permanent failures, send them to a DLQ, and keep the main consumer moving.

#### **12. Optimistic Locking - Concurrent Portfolio Updates**

**❌ BAD:**
```java
@Entity
public class Portfolio {
    @Id
    private UUID id;
    private BigDecimal value;
}
```

**✅ GOOD:**
```java
@Entity
public class Portfolio {
    @Id
    private UUID id;
    @Version
    private long version;
    private BigDecimal value;
}

@Service
@RequiredArgsConstructor
public class PortfolioService {
    private final PortfolioRepository portfolioRepository;

    @Retryable(retryFor = OptimisticLockException.class, maxAttempts = 3)
    public Portfolio update(Portfolio portfolio) {
        return portfolioRepository.save(portfolio);
    }
}
```

**Rule:** Use versioned entities and retry on optimistic-lock conflicts when concurrent fills land together.

#### **13. Database per Service - Data Ownership**

**❌ BAD:**
```java
@Service
@RequiredArgsConstructor
public class ReportingService {
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
}
```

**✅ GOOD:**
```java
public final class DataOwnership {
    private DataOwnership() {
    }

    public static final String AUTH = "postgresql";
    public static final String USER = "postgresql";
    public static final String ORDER = "postgresql";
    public static final String MARKET_DATA = "mongodb";
    public static final String NOTIFICATION = "mongodb";
    public static final String ALL = "redis";
}
```

**Rule:** Each service owns its storage; cross-service reads must flow through APIs or Kafka projections.

#### **14. Anti-Corruption Layer - External API Integration**

**❌ BAD:**
```java
public record BinanceTickerResponse(String s, String c, String E) {
}

@Service
public class MarketDataService {
    public void process(BinanceTickerResponse response) {
        mongoTemplate.insert(response);
    }
}
```

**✅ GOOD:**
```java
public record MarketTick(String symbol, BigDecimal price, Instant exchangeTime) {
}

@Component
public class BinanceTickerAdapter {
    public MarketTick toDomain(BinanceTickerResponse response) {
        return new MarketTick(response.symbol(), new BigDecimal(response.closePrice()), Instant.ofEpochMilli(response.eventTime()));
    }
}
```

**Rule:** Map external payloads into domain objects at the boundary, then keep internal code free of vendor-specific shapes.

#### **15. Health Check Strategy - Kubernetes Liveness/Readiness**

**❌ BAD:**
```java
@RestController
public class HealthController {
    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}
```

**✅ GOOD:**
```java
@Configuration
public class HealthConfiguration {
    @Bean
    HealthIndicator redisHealthIndicator(StringRedisTemplate redisTemplate) {
        return () -> Health.up().build();
    }
}

// Spring Boot Actuator exposes liveness and readiness probes separately.
```

**Rule:** Expose dependency-aware health checks so Kubernetes can distinguish restart conditions from routing readiness.

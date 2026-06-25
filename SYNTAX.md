# TradePulse Syntax Rules

Concrete bad/good examples for implementing TradePulse services. For architectural context, see [DESIGN_PATTERNS.md](DESIGN_PATTERNS.md). For quick project memory, see [CLAUDE.md](CLAUDE.md).

---

#### **1. Monetary Values - Use BigDecimal, Never Float/Double**

**❌ BAD:**
```java
public class OrderRequest {
    private double price;
    private double quantity;

    public double total() {
        return price * quantity;
    }
}
```

**✅ GOOD:**
```java
import java.math.BigDecimal;
import java.math.RoundingMode;

public class OrderRequest {
    private BigDecimal price;
    private BigDecimal quantity;

    public BigDecimal total() {
        return price.multiply(quantity).setScale(8, RoundingMode.HALF_UP);
    }
}
```

**Rule:** All monetary values MUST use `DECIMAL(18,8)` in SQL and `BigDecimal` in Java.

#### **2. Spring Dependency Injection - Constructor Injection Only**

**❌ BAD:**
```java
@Service
public class OrderService {
    @Autowired
    private OrderRepository orderRepository;
}
```

**✅ GOOD:**
```java
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
}
```

**Rule:** Use constructor injection with final fields; never use field injection.

#### **3. Kafka Message Keys - Deterministic Ordering**

**❌ BAD:**
```java
kafkaTemplate.send("order-events", UUID.randomUUID().toString(), event);
kafkaTemplate.send("market-data", null, tick);
```

**✅ GOOD:**
```java
kafkaTemplate.send("order-events", event.orderId().toString(), event);
kafkaTemplate.send("portfolio-events", event.userId().toString(), event);
kafkaTemplate.send("market-data", tick.symbol(), tick);
```

**Rule:** Kafka keys MUST be deterministic: `order_id`, `user_id`, or `symbol`.

#### **4. Redis Key Naming - Consistent Prefix Pattern**

**❌ BAD:**
```java
redisTemplate.opsForValue().set("BTCUSDT", price);
redisTemplate.opsForValue().set("btc:price", price);
redisTemplate.opsForValue().set("price_ETHUSDT", price);
```

**✅ GOOD:**
```java
import java.time.Duration;

public final class RedisKeys {
    private RedisKeys() {
    }

    public static String price(String symbol) {
        return "price:" + symbol.toUpperCase();
    }
}

redisTemplate.opsForValue().set(RedisKeys.price("btcusdt"), price.toPlainString(), Duration.ofSeconds(30));
```

**Rule:** Redis keys MUST follow the documented prefix pattern, use uppercase symbols, and set TTL when data is temporary.

#### **5. Transaction Boundaries - Service Layer Only**

**❌ BAD:**
```java
@RestController
public class OrderController {
    @Transactional
    @PostMapping("/api/orders")
    public OrderResponse place(@RequestBody PlaceOrderRequest request) {
        return orderRepository.save(request.toEntity());
    }
}
```

**✅ GOOD:**
```java
@RestController
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @PostMapping("/api/orders")
    public OrderResponse place(@Valid @RequestBody PlaceOrderRequest request) {
        return orderService.place(request);
    }
}

@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;

    @Transactional
    public OrderResponse place(PlaceOrderRequest request) {
        Order order = orderRepository.save(Order.from(request));
        return OrderResponse.from(order);
    }
}
```

**Rule:** Transaction boundaries MUST be at the service layer only.

#### **6. Exception Handling - Domain Exceptions with @ControllerAdvice**

**❌ BAD:**
```java
if (order == null) {
    throw new RuntimeException("order not found");
}
```

**✅ GOOD:**
```java
public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(UUID orderId) {
        super("Order not found: " + orderId);
    }
}

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(OrderNotFoundException.class)
    ResponseEntity<ApiError> handle(OrderNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("ORDER_NOT_FOUND", ex.getMessage()));
    }
}
```

**Rule:** Create domain-specific exceptions and handle them globally.

#### **7. Kafka Consumer Configuration - Idempotency and Error Handling**

**❌ BAD:**
```java
@KafkaListener(topics = "portfolio-events")
public void consume(PortfolioEvent event) {
    portfolioService.apply(event);
}
```

**✅ GOOD:**
```java
@KafkaListener(topics = "portfolio-events", groupId = "portfolio-service")
public void consume(PortfolioEvent event) {
    if (eventLogRepository.existsByEventId(event.eventId())) {
        log.info("Skipping duplicate event: eventId={}", event.eventId());
        return;
    }

    try {
        portfolioService.apply(event);
        eventLogRepository.save(EventLog.processed(event.eventId()));
    } catch (Exception ex) {
        kafkaTemplate.send("portfolio-events-dlq", event.userId().toString(), event);
        log.error("Portfolio event failed: eventId={}", event.eventId(), ex);
    }
}
```

**Rule:** Kafka consumers MUST be idempotent and route failed messages to a DLQ.

#### **8. SQL Queries - No String Concatenation, Use Named Parameters**

**❌ BAD:**
```java
String sql = "select * from orders where user_id = '" + userId + "'";
jdbcTemplate.queryForList(sql);
```

**✅ GOOD:**
```java
public interface OrderRepository extends JpaRepository<Order, UUID> {
    @Query("""
            select o
            from Order o
            where o.userId = :userId
              and o.status = :status
            """)
    List<Order> findByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") OrderStatus status);
}
```

**Rule:** Use Spring Data or named parameters; never concatenate SQL.

#### **9. Audit Logging - Append-Only, No Updates**

**❌ BAD:**
```java
auditLog.setStatus(order.getStatus());
auditLogRepository.save(auditLog);
```

**✅ GOOD:**
```java
@Document(collection = "order_audit_log")
public record OrderAuditLog(
        String id,
        UUID orderId,
        UUID eventId,
        String eventType,
        OrderSnapshot snapshot,
        Instant createdAt
) {
    public static OrderAuditLog from(Order order, UUID eventId, String eventType) {
        return new OrderAuditLog(null, order.getId(), eventId, eventType, OrderSnapshot.from(order), Instant.now());
    }
}

mongoTemplate.insert(OrderAuditLog.from(order, eventId, "ORDER_FILLED"));
```

**Rule:** Audit logs MUST be append-only MongoDB inserts with full state snapshots.

#### **10. WebSocket Configuration - STOMP with Authentication**

**❌ BAD:**
```java
registry.addEndpoint("/ws").setAllowedOrigins("*").withSockJS();
```

**✅ GOOD:**
```java
@Override
public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws/market")
            .setAllowedOrigins("https://tradepulse.example.com")
            .addInterceptors(jwtHandshakeInterceptor);
}

@Override
public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(jwtConnectChannelInterceptor);
}
```

**Rule:** WebSocket connections MUST validate JWT on STOMP `CONNECT` and whitelist CORS origins.

#### **11. Secrets Management - Never Hardcode, Use AWS Secrets Manager**

**❌ BAD:**
```yaml
spring:
  datasource:
    password: super-secret-password
```

**✅ GOOD:**
```java
@Service
@RequiredArgsConstructor
public class DatabaseSecretProvider {
    private final SecretsManagerClient secretsManagerClient;

    public String readDatabasePassword() {
        GetSecretValueResponse response = secretsManagerClient.getSecretValue(
                GetSecretValueRequest.builder().secretId("tradepulse/auth-service/database").build()
        );
        return response.secretString();
    }
}
```

**Rule:** All secrets come from AWS Secrets Manager; never commit credentials or `.env` files.

#### **12. DTO Validation - Use Jakarta Bean Validation**

**❌ BAD:**
```java
@PostMapping("/api/orders")
public OrderResponse place(@RequestBody PlaceOrderRequest request) {
    if (request.symbol() == null || request.quantity().signum() <= 0) {
        throw new IllegalArgumentException("invalid request");
    }
    return orderService.place(request);
}
```

**✅ GOOD:**
```java
public record PlaceOrderRequest(
        @NotBlank String symbol,
        @NotNull OrderSide side,
        @NotNull @DecimalMin(value = "0.00000001") BigDecimal quantity,
        @DecimalMin(value = "0.00000001") BigDecimal limitPrice
) {
}

@PostMapping("/api/orders")
public OrderResponse place(@Valid @RequestBody PlaceOrderRequest request) {
    return orderService.place(request);
}
```

**Rule:** Use Jakarta Bean Validation annotations on DTOs and `@Valid` in controllers.

#### **13. MapStruct - Type-Safe Entity-DTO Mapping**

**❌ BAD:**
```java
OrderResponse response = new OrderResponse();
response.setId(order.getId());
response.setSymbol(order.getSymbol());
```

**✅ GOOD:**
```java
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface OrderMapper {
    OrderResponse toResponse(Order order);

    Order toEntity(PlaceOrderRequest request);
}
```

**Rule:** Use MapStruct for conversions and fail on unmapped fields.

#### **14. Logging - Structured Logging with Correlation IDs**

**❌ BAD:**
```java
System.out.println("Order placed");
e.printStackTrace();
```

**✅ GOOD:**
```java
@Slf4j
@Service
public class OrderService {
    public OrderResponse place(PlaceOrderRequest request) {
        MDC.put("correlationId", request.correlationId().toString());
        log.info("Order placed: userId={}, symbol={}, side={}", request.userId(), request.symbol(), request.side());
        return orderResponse;
    }
}
```

**Rule:** Use SLF4J with MDC and include business context such as `symbol`, `orderId`, and `userId`.

#### **15. Testing - Use Testcontainers for Integration Tests**

**❌ BAD:**
```java
@SpringBootTest
class OrderServiceIntegrationTest {
    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;
}
```

**✅ GOOD:**
```java
@SpringBootTest
@Testcontainers
class OrderServiceIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }
}
```

**Rule:** Integration tests use Testcontainers; unit tests mock collaborators only.

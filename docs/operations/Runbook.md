# TradePulse — Operations Runbook

> For architecture context, see [Architecture.md](../architecture/Architecture.md).
> For API reference, see [ApiContracts.md](../api/ApiContracts.md).
> For task tracking, see [tasks.md](../planning/tasks.md).

---

## 1. Prerequisites

| Tool | Version | Check |
|---|---|---|
| Java | 21+ | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| Docker | 24+ | `docker --version` |
| Docker Compose | v2 | `docker compose version` |

---

## 2. Infrastructure Setup

All local infrastructure (PostgreSQL, MongoDB, Redis, Kafka, Zookeeper) runs via Docker Compose.

```bash
# Start all infrastructure containers
cd docker && docker-compose up -d

# Verify containers are running
docker ps
```

### Container Reference

> **⚠️ Added beyond plan:** Container names + credentials table not in any source file.

| Container Name | Service | Port | Credentials |
|---|---|---|---|
| `tradepulse-postgres` | PostgreSQL 15 | 5432 | `tradepulse` / `tradepulse` |
| `tradepulse-mongodb` | MongoDB 7 | 27017 | `tradepulse` / `tradepulse` |
| `tradepulse-redis` | Redis 7 | 6379 | password: `tradepulse` |
| `tradepulse-kafka` | Kafka 3.6 | 9092 | — |
| `tradepulse-zookeeper` | Zookeeper | 2181 | — |

### Connection Strings (local)

```
PostgreSQL: jdbc:postgresql://localhost:5432/<db>?user=tradepulse&password=tradepulse
MongoDB:    mongodb://tradepulse:tradepulse@localhost:27017/<db>?authSource=admin
Redis:      redis://:tradepulse@localhost:6379
Kafka:      localhost:9092
```

---

## 3. Starting Services

### Option A — Using `start-services.sh` (Recommended)

```bash
# Start ALL services (background, with log files)
./scripts/start-services.sh

# Start specific services only
./scripts/start-services.sh auth user order

# Check status of all services
./scripts/start-services.sh --status

# Tail logs for a service in real time
./scripts/start-services.sh --logs market-data

# Stop all services
./scripts/start-services.sh --stop

# Stop specific services
./scripts/start-services.sh --stop auth order
```

**Script behaviour:**
- Checks Docker infrastructure containers before starting
- Stagger-starts services (2s delay each) to avoid DB/port contention
- Saves PID files to `scripts/pids/`
- Saves log files to `scripts/logs/`
- Skips already-running services (idempotent)
- Graceful stop: SIGTERM → wait 30s → SIGKILL

### Option B — Manual Maven (Foreground)

```bash
# Start a single service in foreground (useful for debugging)
mvn spring-boot:run -pl services/auth-service
mvn spring-boot:run -pl services/user-service
mvn spring-boot:run -pl services/order-service
mvn spring-boot:run -pl services/market-data-service
mvn spring-boot:run -pl services/matching-engine
mvn spring-boot:run -pl services/portfolio-service
mvn spring-boot:run -pl services/notification-service
mvn spring-boot:run -pl services/reporting-service
mvn spring-boot:run -pl services/api-gateway-service
```

---

## 4. Service Port Map

> **⚠️ Added beyond plan:** Consolidated port + health endpoint table.

| Service | Port | Maven Module | Health Endpoint |
|---|---|---|---|
| api-gateway-service | 8080 | `services/api-gateway-service` | `GET :8080/actuator/health` |
| auth-service | 8081 | `services/auth-service` | `GET :8081/api/auth/actuator/health` |
| user-service | 8082 | `services/user-service` | `GET :8082/api/users/actuator/health` |
| order-service | 8083 | `services/order-service` | `GET :8083/api/orders/actuator/health` |
| market-data-service | 8084 | `services/market-data-service` | `GET :8084/api/market/actuator/health` |
| portfolio-service | 8085 | `services/portfolio-service` | `GET :8085/api/portfolio/actuator/health` |
| notification-service | 8086 | `services/notification-service` | `GET :8086/api/actuator/health` |
| reporting-service | 8087 | `services/reporting-service` | `GET :8087/api/reports/actuator/health` |
| matching-engine | 8090 | `services/matching-engine` | `GET :8090/actuator/health` |

### Quick Health Check (all services)

```bash
for service in "8080:/actuator/health" "8081:/api/auth/actuator/health" "8082:/api/users/actuator/health" "8083:/api/orders/actuator/health" "8084:/api/market/actuator/health" "8085:/api/portfolio/actuator/health" "8086:/api/actuator/health" "8087:/api/reports/actuator/health" "8090:/actuator/health"; do
  port="${service%%:*}"
  path="${service#*:}"
  echo -n "Port $port ($path): "
  svc_status=$(curl -s "http://localhost:$port$path" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('status','?'))" 2>/dev/null || echo "UNREACHABLE")
  echo "$svc_status"
done
```

---

## 5. Log Management

All logs are written to `scripts/logs/` when using `start-services.sh`.

```
scripts/
├── logs/
│   ├── api-gateway-service.log
│   ├── auth-service.log
│   ├── user-service.log
│   ├── market-data-service.log
│   ├── order-service.log
│   ├── matching-engine.log
│   ├── portfolio-service.log
│   ├── notification-service.log
│   └── reporting-service.log
└── pids/
    ├── auth-service.pid
    └── ...
```

```bash
# Tail a specific service log
./scripts/start-services.sh --logs auth

# Or directly
tail -f scripts/logs/auth-service.log

# Search for errors
grep -i "error\|exception" scripts/logs/auth-service.log

# Search for startup success
grep "Started" scripts/logs/auth-service.log
```

---

## 6. Build Commands

```bash
# Full build, skip tests (fastest)
mvn clean install -DskipTests

# Full build with tests
mvn clean install

# Build a single service
mvn clean install -DskipTests -pl services/auth-service

# Build with dependency resolution
mvn clean install -DskipTests -pl services/auth-service -am
```

---

## 7. Test Commands

```bash
# Run all tests
mvn test

# Run tests for a single service
mvn test -pl services/auth-service

# Run a specific test class
mvn test -pl services/order-service -Dtest=OrderServiceTest

# Run integration tests only
mvn test -pl services/auth-service -Dtest=*IntegrationTest

# Skip tests
mvn clean install -DskipTests
```

---

## 8. Troubleshooting

### Common Errors and Fixes

> **⚠️ Added beyond plan:** Documents actual errors encountered during the development session.

#### `SchemaManagementException: Schema-validation: missing table`
- **Cause:** `spring.jpa.hibernate.ddl-auto: validate` fails because tables don't exist yet.
- **Fix:** Set `ddl-auto: update` in `application.yml` for local dev. (**Never use `update` in production.**)

#### `MongoCommandException: Authentication failed`
- **Cause:** MongoDB connection URI missing credentials.
- **Fix:** Use `mongodb://tradepulse:tradepulse@localhost:27017/<db>?authSource=admin`

#### `RedisConnectionFailureException: NOAUTH Authentication required`
- **Cause:** Redis password not set in application config.
- **Fix:** Set `spring.data.redis.password: tradepulse` in `application.yml`

#### `UnsatisfiedDependencyException: SesClient` (notification-service)
- **Cause:** AWS SDK tries to inject real `SesClient` but no AWS credentials exist locally.
- **Fix:** `MailConfig.java` uses a Java dynamic proxy mock of `SesClient`. Already applied.

#### `IllegalArgumentException: Could not resolve placeholder binance.websocket.streams`
- **Cause:** Config binding expects a comma-separated String but YAML had list format.
- **Fix:** Change `application.yml` to use comma-separated: `streams: "btcusdt@ticker,ethusdt@ticker,solusdt@ticker"`

#### `No qualifying bean of type JwtDecoder` (user-service)
- **Cause:** Missing `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` configuration.
- **Fix:** Add `jwk-set-uri: http://localhost:8081/.well-known/jwks.json` to `application.yml`.

#### `DnsServerAddressStreamProvider` warning (Netty, macOS)
- **Cause:** Netty DNS resolution warning on macOS — cosmetic only.
- **Fix:** Safe to ignore for local dev. Does not affect functionality.

#### Port already in use (`Address already in use: 8081`)
- **Fix:**
  ```bash
  # Find process using port
  lsof -i :8081
  # Kill it
  kill -9 <PID>
  # Or use the stop script
  ./scripts/start-services.sh --stop auth
  ```

#### Service started but immediately exits
- **Check:** Look at the log file for startup errors
  ```bash
  tail -50 scripts/logs/<service>.log | grep -i "error\|exception\|caused by"
  ```

---

## 9. Useful Dev Commands

```bash
# Check Kafka topics
docker exec tradepulse-kafka kafka-topics.sh --list --bootstrap-server localhost:9092

# Tail Kafka topic messages
docker exec tradepulse-kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic order-events \
  --from-beginning

# Check Redis keys
docker exec tradepulse-redis redis-cli -a tradepulse KEYS "*"

# Check Redis price cache
docker exec tradepulse-redis redis-cli -a tradepulse GET price:BTCUSDT

# Check MongoDB collections
docker exec tradepulse-mongodb mongosh -u tradepulse -p tradepulse --authenticationDatabase admin

# Reset all infrastructure (DESTRUCTIVE)
cd docker && docker-compose down -v && docker-compose up -d
```

---

## 10. AWS Login & Terraform Credentials

If you encounter `ExpiredToken` or `No valid credential sources found` when running `terraform apply`, export your AWS credentials dynamically using this one-liner (requires `jq`):

```bash
eval $(aws configure export-credentials | jq -r '"export AWS_ACCESS_KEY_ID=\(.AccessKeyId)\nexport AWS_SECRET_ACCESS_KEY=\(.SecretAccessKey)\nexport AWS_SESSION_TOKEN=\(.SessionToken)"')
```

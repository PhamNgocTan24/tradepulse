# TradePulse — Task & Roadmap

> For architecture context, see [Architecture.md](../architecture/Architecture.md).
> For operations, see [Runbook.md](../operations/Runbook.md).

---

## 1. Functional Requirements

| ID | Requirement | Status |
|---|---|---|
| FR-01 | User registration & login (email/password + Google OAuth2 + optional 2FA via TOTP) | 🔨 In Progress |
| FR-02 | Each user starts with $100,000 USD virtual balance | 🔨 In Progress |
| FR-03 | Place Market Orders (execute immediately at current price) | 🔨 In Progress |
| FR-04 | Place Limit Orders (execute when price hits target) | 🔨 In Progress |
| FR-05 | Place Stop-Loss Orders (sell when price drops to threshold) | ⬜ Pending |
| FR-06 | Cancel open (pending) orders | 🔨 In Progress |
| FR-07 | Real-time portfolio view — current holdings, P&L (unrealized + realized) | ⬜ Pending |
| FR-08 | Real-time crypto price feed via WebSocket (from Binance public market data) | 🔨 In Progress |
| FR-09 | Global leaderboard — top 100 traders ranked by portfolio value | ⬜ Pending |
| FR-10 | Price alert subscription — notify when asset crosses a price threshold | ⬜ Pending |
| FR-11 | Trade history with pagination, filtering, sorting | ⬜ Pending |
| FR-12 | Generate PDF portfolio report (downloadable, stored on S3) | ⬜ Pending |
| FR-13 | Admin dashboard — user management, circuit breaker status, system health | ⬜ Pending |

---

## 2. Non-Functional Requirements

| ID | Requirement | Target | Status |
|---|---|---|---|
| NFR-01 | Order matching latency | < 100ms p99 | ⬜ Pending |
| NFR-02 | Market data feed — client-facing snapshots pushed on change | ≥ every 5 seconds | ⬜ Pending |
| NFR-03 | Concurrent WebSocket connections | 10,000 | ⬜ Pending |
| NFR-04 | API rate limiting per user (enforced via Redis) | 60 req/min | 🔨 In Progress |
| NFR-05 | All REST endpoints authenticated via JWT | 15-min expiry + refresh | 🔨 In Progress |
| NFR-06 | HTTPS only; secrets managed via AWS Secrets Manager | — | ⬜ Pending (local: mock) |
| NFR-07 | Full audit log of every order event (immutable, append-only in MongoDB) | — | 🔨 In Progress |

---

## 3. Implementation Roadmap

### Phase 1 — Foundation (Week 1–2)

- [x] Setup monorepo with Maven multi-module
- [x] `common-dto` module with Kafka event schemas
- [x] `auth-service` — register, login, JWT (RS256), JWKS endpoint
- [x] `user-service` — profiles, virtual balance
- [x] Docker Compose for local: PostgreSQL, MongoDB, Redis, Kafka, Zookeeper
- [x] API Gateway with JWT filter + rate limiting

### Phase 2 — Core Trading (Week 3–4)

- [x] `market-data-service` — Binance WebSocket ingestion, Kafka publish, Redis cache, client WebSocket push
- [x] `order-service` — place/cancel orders, Kafka publish
- [ ] `matching-engine` — OrderBook DSA, Kafka consumer/producer
- [ ] `portfolio-service` — holdings, balance update, P&L calculation

### Phase 3 — Enrichment (Week 5)

- [ ] `notification-service` — price alerts, WebSocket push, AWS SES
- [ ] `reporting-service` — PDF generation, S3 upload, pre-signed URLs
- [ ] Leaderboard (Redis sorted set)
- [ ] Google OAuth2
- [ ] TOTP 2FA

### Phase 4 — Production Readiness (Week 6–7)

- [ ] Terraform modules for AWS (VPC, EKS, RDS, ElastiCache, MSK, S3)
- [ ] Kubernetes manifests + HPA
- [ ] CI/CD pipeline (GitHub Actions → ECR → EKS)
- [ ] CloudWatch dashboards + alarms
- [ ] OWASP Dependency Check in CI
- [ ] Load test with k6 (validate 10k WebSocket connections)

---

## 4. Session Progress

> **⚠️ Added beyond plan:** This section tracks fixes and infrastructure work done during the development session — useful for understanding what the current state of running services is.

### Local Dev — Services Running

| Service | Port | Status | Notes |
|---|---|---|---|
| api-gateway-service | 8080 | ✅ Running | JWT validation, routing |
| auth-service | 8081 | ✅ Running | Ephemeral RSA key (local), JWKS endpoint |
| user-service | 8082 | ✅ Running | — |
| order-service | 8084 | ✅ Running | MongoDB audit log |
| market-data-service | 8083 | ✅ Running | Binance WS connected |
| matching-engine | 8086 | ✅ Running | — |
| portfolio-service | 8085 | ✅ Running | — |
| notification-service | 8087 | ✅ Running | AWS SES mocked |
| reporting-service | 8088 | ✅ Running | — |

### Fixes Applied During Session

| Area | Fix | File(s) |
|---|---|---|
| DB schema | Changed `ddl-auto: validate` → `update` across all services | `application.yml` (all services) |
| auth-service | Added ephemeral RSA key generation for local dev (bypasses AWS SM) | `KeyProviderConfig.java` |
| auth-service | Implemented `/.well-known/jwks.json` endpoint | `AuthController.java` |
| auth-service | Added `kid` header to signed JWT tokens | `JwtUtils.java` |
| MongoDB | Fixed URI to include credentials `tradepulse:tradepulse` | `application.yml` (market-data, order, notification) |
| Redis | Fixed password fallback to `tradepulse` | `application.yml` (all services) |
| notification-service | Mocked AWS SES client with dynamic proxy to allow local startup | `MailConfig.java` |
| market-data-service | Changed `binance.websocket.streams` from list to comma-separated string | `application.yml` |
| user-service | Added `jwk-set-uri` pointing to auth-service for `JwtDecoder` bean | `application.yml` |

---

## 5. Tech Stack Summary

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
| Code Quality | Checkstyle, OWASP Dependency Check, SonarQube (optional) | — |

# Active Sprint Tasks & Operations

This document tracks active development tasks, running services, and fixes applied during the current session.

---

## 1. Active Tasks (Sprint Backlog)

**Sprint Goal:** Complete Core Trading Flow (Matching Engine & Portfolio updates).

- [x] Set up multi-module monorepo and dependencies
- [x] Configure security & JWKS validation in API Gateway
- [x] Ingest Binance market data via WebSockets and publish to Kafka
- [x] Save orders and write to MongoDB audit log in `order-service`
- [x] Implement memory-based OrderBook matching in `matching-engine`
- [x] Consume trade events in `portfolio-service` and update holdings
- [x] Implement real-time portfolio value updates using Redis prices

---

## 2. Local Dev Services running

| Service | Port | Status | Notes |
|---|---|---|---|
| api-gateway-service | 8080 | ✅ Running | JWT validation, routing |
| auth-service | 8081 | ✅ Running | Ephemeral RSA key (local), JWKS endpoint |
| user-service | 8082 | ✅ Running | Profile data provider |
| market-data-service | 8083 | ✅ Running | Ingests Binance WebSocket data |
| order-service | 8084 | ✅ Running | Manages CRUD and writes to MongoDB audit log |
| portfolio-service | 8085 | ✅ Running | Tracks user asset allocations |
| matching-engine | 8086 | ✅ Running | Matches buy/sell orders in memory |
| notification-service | 8087 | ✅ Running | AWS SES email notifications (mocked) |
| reporting-service | 8088 | ✅ Running | PDF statements generator |

---

## 3. Fixes Applied in Current Session

| Area | Fix | File(s) |
|---|---|---|
| **DB Schema** | Changed `ddl-auto: validate` ➡️ `update` to allow automated schema migration. | `application.yml` (all services) |
| **auth-service** | Added ephemeral RSA key generation for local dev to bypass AWS Secrets Manager setup. | `KeyProviderConfig.java` |
| **auth-service** | Implemented `/.well-known/jwks.json` endpoint to share JWKS. | `AuthController.java` |
| **auth-service** | Added `kid` header to signed JWT tokens for proper key resolution. | `JwtUtils.java` |
| **MongoDB** | Added credentials `tradepulse:tradepulse` to local connection URI. | `application.yml` (market-data, order, notification) |
| **Redis** | Fixed password fallback default to `tradepulse` for authentication compatibility. | `application.yml` (all services) |
| **notification-service** | Mocked AWS SES client using dynamic proxies to allow local startup without AWS credentials. | `MailConfig.java` |
| **market-data-service** | Changed `binance.websocket.streams` config structure from List to comma-separated String. | `application.yml` |
| **user-service** | Appended `jwk-set-uri` configuration pointing to auth-service for JWT validation. | `application.yml` |

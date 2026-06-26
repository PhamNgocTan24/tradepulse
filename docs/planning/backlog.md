# TradePulse — Product Backlog

This document defines the Functional (FR) and Non-Functional (NFR) Requirements for the TradePulse platform as structured Product Backlog Items (PBIs) to guide AI development.

---

## 1. Backlog Overview

### Functional Requirements (FR)

| ID | PBI / Requirement | Priority | Target Component | Status |
|---|---|---|---|---|
| **FR-01** | User registration & login (email/password + Google OAuth2 + optional 2FA via TOTP) | Must | `auth-service`, `user-service`, `api-gateway` | 🔨 In Progress |
| **FR-02** | Each user starts with $100,000 USD virtual balance | Must | `user-service` | 🔨 In Progress |
| **FR-03** | Place Market Orders (execute immediately at current price) | Must | `order-service`, `matching-engine` | 🔨 In Progress |
| **FR-04** | Place Limit Orders (execute when price hits target) | Must | `order-service`, `matching-engine` | 🔨 In Progress |
| **FR-05** | Place Stop-Loss Orders (sell when price drops to threshold) | Should | `order-service`, `matching-engine` | ⬜ Pending |
| **FR-06** | Cancel open (pending) orders | Must | `order-service`, `matching-engine` | 🔨 In Progress |
| **FR-07** | Real-time portfolio view — current holdings, P&L (unrealized + realized) | Must | `portfolio-service` | ⬜ Pending |
| **FR-08** | Real-time crypto price feed via WebSocket (from Binance public market data) | Must | `market-data-service` | 🔨 In Progress |
| **FR-09** | Global leaderboard — top 100 traders ranked by portfolio value | Could | `portfolio-service` | ⬜ Pending |
| **FR-10** | Price alert subscription — notify when asset crosses a price threshold | Should | `notification-service` | ⬜ Pending |
| **FR-11** | Trade history with pagination, filtering, sorting | Should | `order-service` | ⬜ Pending |
| **FR-12** | Generate PDF portfolio report (downloadable, stored on S3) | Could | `reporting-service` | ⬜ Pending |
| **FR-13** | Admin dashboard — user management, circuit breaker status, system health | Could | `api-gateway`, `admin-dashboard` | ⬜ Pending |

### Non-Functional Requirements (NFR)

| ID | PBI / Requirement | Priority | Target | Status |
|---|---|---|---|---|
| **NFR-01** | Order matching latency | Must | < 100ms p99 | ⬜ Pending |
| **NFR-02** | Market data feed — client-facing snapshots pushed on change | Must | ≥ every 5 seconds | ⬜ Pending |
| **NFR-03** | Concurrent WebSocket connections | Should | 10,000 | ⬜ Pending |
| **NFR-04** | API rate limiting per user (enforced via Redis) | Must | 60 req/min | 🔨 In Progress |
| **NFR-05** | All REST endpoints authenticated via JWT | Must | 15-min expiry + refresh | 🔨 In Progress |
| **NFR-06** | HTTPS only; secrets managed via AWS Secrets Manager | Must | Production deploy | ⬜ Pending |
| **NFR-07** | Full audit log of every order event (immutable, append-only in MongoDB) | Must | `order_audit_log` collection | 🔨 In Progress |

---

## 2. Core Epics & Key User Stories

### EPIC-AUTH: Authentication & User Profile
#### [US-AUTH-01] JWT Authentication (RS256)
- **User Story:** As a client, I want to authenticate via username/password and receive a signed JWT token so that I can access protected endpoints.
- **Acceptance Criteria (DoD):**
  - **AC-1:** POST `/api/v1/auth/login` returns access token (15m expiry) and refresh token (7d expiry).
  - **AC-2:** JWT signature uses RS256 with public/private keys.
  - **AC-3:** JWKS endpoint `/.well-known/jwks.json` exposes active public key.
  - **AC-4:** Invalid credentials return `401 Unauthorized`.

#### [US-AUTH-02] Virtual Balance Initialization
- **User Story:** As a newly registered trader, I want to be automatically provisioned with a virtual balance of $100,000 USD so that I can start trading.
- **Acceptance Criteria (DoD):**
  - **AC-1:** On successful user registration, trigger creation of a portfolio balance in PostgreSQL with exactly `100000.00000000`.
  - **AC-2:** Verify balance initialized value can be fetched at GET `/api/v1/users/me/balance`.

### EPIC-ORDER: Order Management
#### [US-ORD-01] Place Limit/Market Orders
- **User Story:** As a trader, I want to submit buy/sell orders (Market and Limit) so that I can trade assets.
- **Acceptance Criteria (DoD):**
  - **AC-1:** POST `/api/v1/orders` accepts `PlaceOrderRequest` (symbol, side [BUY/SELL], type [LIMIT/MARKET], quantity, price).
  - **AC-2:** Validate quantities and prices are positive numbers up to 8 decimal places (use `BigDecimal`).
  - **AC-3:** Check virtual balance before submitting orders. If insufficient, reject with `INSUFFICIENT_BALANCE`.
  - **AC-4:** Save order in Postgres `orders` table. Emit `NEW_ORDER` event to Kafka `order-events` topic.
  - **AC-5:** Write initial snapshot state to MongoDB `order_audit_log` (append-only).

### EPIC-MATCH: Real-Time Matching Engine
#### [US-MATCH-01] Order Execution
- **User Story:** As the matching engine, I want to consume orders from Kafka, build in-memory order books, and perform price-time matching so that trades are executed quickly.
- **Acceptance Criteria (DoD):**
  - **AC-1:** Rebuild order book state on startup by reading unprocessed events from Kafka.
  - **AC-2:** Match buy limit orders with sell limit orders at or below the buy price.
  - **AC-3:** Match market orders immediately against the best available price in the order book.
  - **AC-4:** Publish matching result (`ORDER_FILLED` or `PARTIAL_FILL`) to `order-events` and updates to `portfolio-events`.
  - **AC-5:** Ensure matching hot-path does not execute any synchronous database calls (<100ms p99 target).

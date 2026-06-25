# TradePulse API Contracts

> This file defines the public API expectations for each service in TradePulse.
> For architecture context, see [Architecture.md](../architecture/Architecture.md).
> For implementation syntax, see [Syntax.md](../development/Syntax.md).

---

## 1. API Principles

- All REST APIs use JSON.
- All write endpoints return domain responses, not raw entities.
- All services use versioned base paths: `/api/v1/...`
- Monetary values are serialized as strings and mapped to `BigDecimal`.
- Error responses use a stable envelope across all services.
- Gateway is the public entry point; internal services are not exposed directly in production.

### Standard Success Shape

```json
{
  "data": {
    "id": "2d3e8f4a-7f37-4a0e-8ef2-0a5c5e7f2d8d"
  },
  "meta": {
    "requestId": "c2a7c9e3d0f34c3f"
  }
}
```

### Standard Error Shape

```json
{
  "error": {
    "code": "ORDER_NOT_FOUND",
    "message": "Order not found: 2d3e8f4a-7f37-4a0e-8ef2-0a5c5e7f2d8d",
    "details": [
      {
        "field": "orderId",
        "message": "must not be null"
      }
    ]
  },
  "meta": {
    "requestId": "c2a7c9e3d0f34c3f"
  }
}
```

---

## 2. Common Headers

| Header | Direction | Purpose |
|---|---|---|
| `Authorization: Bearer <jwt>` | Request | Authenticated routes |
| `X-Request-Id: <uuid>` | Request | Tracing when Gateway does not inject |
| `Content-Type: application/json` | Both | JSON body |
| `Accept: application/json` | Request | Expected response format |
| `X-User-Id: <uuid>` | Internal | Injected by Gateway after JWT validation |

---

## 3. Auth Service

Base path: `/api/v1/auth`

### POST `/register`

Request:
```json
{
  "email": "trader@example.com",
  "password": "StrongPassword123!",
  "displayName": "Trader One"
}
```

Response:
```json
{
  "data": {
    "userId": "c0d1d43c-0d9c-4a4d-8e2c-7ed1dbce7f56",
    "email": "trader@example.com"
  }
}
```

### POST `/login`

Request:
```json
{
  "email": "trader@example.com",
  "password": "StrongPassword123!"
}
```

Response:
```json
{
  "data": {
    "accessToken": "eyJhbGciOiJSUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJSUzI1NiJ9...",
    "expiresInSeconds": 900
  }
}
```

### POST `/refresh`

Request:
```json
{
  "refreshToken": "eyJhbGciOiJSUzI1NiJ9..."
}
```

Response:
```json
{
  "data": {
    "accessToken": "eyJhbGciOiJSUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJSUzI1NiJ9...",
    "expiresInSeconds": 900
  }
}
```

### POST `/logout`

Invalidates the current `jti` via Redis blacklist (`blacklist:{jti}`, TTL = token remaining lifetime).

### POST `/2fa/enable`

Returns QR provisioning URI for TOTP setup (ZXing QR encoded).

### POST `/2fa/verify`

Request: `{ "totpCode": "123456" }`

Verifies a TOTP code and activates the second factor.

### GET `/.well-known/jwks.json`

Returns the RSA public key set used by API Gateway to validate JWT signatures.

```json
{
  "keys": [
    {
      "kty": "RSA",
      "kid": "key-id",
      "use": "sig",
      "alg": "RS256",
      "n": "...",
      "e": "AQAB"
    }
  ]
}
```

---

## 4. User Service

Base path: `/api/v1/users`

### GET `/me`

Response:
```json
{
  "data": {
    "userId": "c0d1d43c-0d9c-4a4d-8e2c-7ed1dbce7f56",
    "displayName": "Trader One",
    "email": "trader@example.com"
  }
}
```

### PUT `/me`

Request:
```json
{
  "displayName": "Trader Two"
}
```

### GET `/me/balance`

Response:
```json
{
  "data": {
    "balance": "100000.00000000",
    "currency": "USD"
  }
}
```

### GET `/leaderboard`

Reads from Redis sorted set `leaderboard` (ZREVRANGE top 100).

Response:
```json
{
  "data": [
    {
      "userId": "c0d1d43c-0d9c-4a4d-8e2c-7ed1dbce7f56",
      "portfolioValue": "142350.12000000",
      "rank": 1
    }
  ]
}
```

---

## 5. Order Service

Base path: `/api/v1/orders`

### POST `/`

Request:
```json
{
  "symbol": "BTCUSDT",
  "side": "BUY",
  "type": "LIMIT",
  "quantity": "0.25000000",
  "limitPrice": "65000.00000000"
}
```

Response:
```json
{
  "data": {
    "orderId": "2d3e8f4a-7f37-4a0e-8ef2-0a5c5e7f2d8d",
    "status": "NEW",
    "symbol": "BTCUSDT",
    "side": "BUY",
    "type": "LIMIT",
    "quantity": "0.25000000",
    "filledQuantity": "0.00000000"
  }
}
```

### GET `/`

Filter parameters: `status`, `symbol`, `side`, `page`, `size`

### GET `/{orderId}`

Returns the current order state.

### DELETE `/{orderId}`

Cancels an open (PENDING or PARTIAL) order. Publishes `ORDER_CANCELLED` to `order-events`.

### Error Notes

- Reject orders with invalid quantities, unsupported symbols, or insufficient balance
- Price and quantity validation must run before the order is persisted
- Supported order types: `MARKET`, `LIMIT`, `STOP_LOSS`

---

## 6. Market Data Service

Base path: `/api/v1/market`

### GET `/prices`

Returns all tracked assets from Redis.

Response:
```json
{
  "data": [
    {
      "symbol": "BTCUSDT",
      "price": "71234.12000000",
      "source": "BINANCE"
    },
    {
      "symbol": "ETHUSDT",
      "price": "3542.18000000",
      "source": "BINANCE"
    }
  ]
}
```

### GET `/prices/{symbol}`

Single asset price from Redis `price:{SYMBOL}`.

### GET `/history/{symbol}`

Query parameters:
- `interval` — e.g. `1h`, `1d`
- `limit` — max records to return

Response:
```json
{
  "data": [
    {
      "symbol": "BTCUSDT",
      "price": "71234.12000000",
      "timestamp": "2026-06-24T13:00:00Z"
    }
  ]
}
```

### WebSocket `/ws/market`

> **⚠️ Added beyond plan:** WebSocket STOMP message format not present in original `API_CONTRACTS.md`.

- Protocol: STOMP over SockJS
- Auth: `Authorization: Bearer <token>` header on `CONNECT` frame
- Subscribe: `/topic/prices` — receives all symbols
- Subscribe: `/topic/prices/{SYMBOL}` — e.g. `/topic/prices/BTCUSDT`

**Server push message format:**
```json
{
  "symbol": "BTCUSDT",
  "price": "71234.12000000",
  "changePct": "2.14",
  "volume": "15678.23400000",
  "timestamp": "2026-06-25T09:00:00Z"
}
```

---

## 7. Portfolio Service

Base path: `/api/v1/portfolio`

### GET `/me`

Response:
```json
{
  "data": {
    "cashBalance": "2500.00000000",
    "positions": [
      {
        "symbol": "BTCUSDT",
        "quantity": "0.25000000",
        "averagePrice": "64000.00000000",
        "marketPrice": "71234.12000000",
        "unrealizedPnl": "1783.53000000"
      }
    ],
    "totalValue": "142350.12000000"
  }
}
```

### GET `/me/history`

Returns historical P&L snapshots over time.

---

## 8. Notification Service

Base path: `/api/v1/notifications`

### GET `/me`

Returns notification history (from MongoDB `notifications` collection).

### POST `/preferences`

Updates notification preferences (email enabled, push enabled).

### POST `/alerts`

Creates a price alert.

Request:
```json
{
  "symbol": "BTCUSDT",
  "condition": "ABOVE",
  "targetPrice": "75000.00000000"
}
```

### DELETE `/alerts/{id}`

### GET `/alerts`

Lists active price alerts for the authenticated user.

### Delivery Channels

- Kafka consumer (`notifications` topic)
- WebSocket push
- Email via AWS SES when configured

---

## 9. Reporting Service

Base path: `/api/v1/reports`

### POST `/portfolio`

Triggers async PDF report generation.

Request:
```json
{
  "userId": "c0d1d43c-0d9c-4a4d-8e2c-7ed1dbce7f56",
  "from": "2026-01-01T00:00:00Z",
  "to":   "2026-06-24T00:00:00Z"
}
```

Response:
```json
{
  "data": {
    "reportId": "8cf2f1d8-7dbe-4f0e-b1b0-bd2d7d8f2c25",
    "status": "GENERATING"
  }
}
```

### GET `/{reportId}`

Returns report status and S3 pre-signed download URL when ready.

```json
{
  "data": {
    "reportId": "8cf2f1d8-7dbe-4f0e-b1b0-bd2d7d8f2c25",
    "status": "READY",
    "downloadUrl": "https://s3.amazonaws.com/...",
    "expiresAt": "2026-06-25T12:00:00Z"
  }
}
```

---

## 10. Gateway Contract

The gateway is the **only public ingress point**.

- JWT validation happens here
- Rate limiting happens here (Redis token bucket)
- Service routing happens here
- Gateway exposes only edge-facing routes, not business logic

### Public Routes

| Route Pattern | Downstream Service |
|---|---|
| `/api/v1/auth/**` | auth-service:8081 |
| `/api/v1/users/**` | user-service:8082 |
| `/api/v1/market/**` | market-data-service:8083 |
| `/api/v1/orders/**` | order-service:8084 |
| `/api/v1/portfolio/**` | portfolio-service:8085 |
| `/api/v1/notifications/**` | notification-service:8087 |
| `/api/v1/reports/**` | reporting-service:8088 |
| `/ws/**` | market-data-service:8083 |

---

## 11. Error Codes

| Code | HTTP | Meaning |
|---|---:|---|
| `VALIDATION_ERROR` | 400 | Request body or params failed validation |
| `UNAUTHORIZED` | 401 | Missing or invalid JWT |
| `FORBIDDEN` | 403 | Insufficient role or permission |
| `NOT_FOUND` | 404 | Resource does not exist |
| `ORDER_NOT_FOUND` | 404 | Order id was not found |
| `INSUFFICIENT_BALANCE` | 409 | User balance cannot cover the order |
| `DUPLICATE_EVENT` | 409 | Kafka event already processed |
| `PRICE_NOT_AVAILABLE` | 503 | Live price missing from Redis |
| `EXTERNAL_SERVICE_UNAVAILABLE` | 503 | Binance or another dependency is unavailable |
| `RATE_LIMITED` | 429 | Request exceeded quota |
| `INTERNAL_SERVER_ERROR` | 500 | Unexpected server failure |

---

## 12. Rate Limiting Rules

- Gateway enforces per-user request limits through Redis
- Default quota: `60 requests/minute/user`
- Auth endpoints may use stricter burst protection
- WebSocket connections counted for connection pressure
- Redis key pattern: `rate_limit:{user_id}` (String, TTL 60s, increment per request)

### Rate Limit Error

```json
{
  "error": {
    "code": "RATE_LIMITED",
    "message": "Rate limit exceeded. Try again later."
  }
}
```

---

## 13. Implementation Notes

- Keep request and response DTOs versioned by API path, not by class name
- Use `enum` values in API payloads only when stable and documented
- Never expose persistence models directly (no JPA entities as response bodies)
- Prefer a single error envelope across all services
- Monetary values always serialized as decimal strings, never `float`/`double`

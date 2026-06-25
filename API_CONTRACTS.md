# TradePulse API Contracts

This file defines the public API expectations for each service in TradePulse. It is intentionally scaffold-friendly: endpoints, payloads, and error behavior are documented now so implementation stays consistent later.

For architecture guidance, see [CLAUDE.md](CLAUDE.md). For syntax-level rules, see [SYNTAX.md](SYNTAX.md).

---

## 1. API Principles

- All REST APIs use JSON.
- All write endpoints return domain responses, not raw entities.
- All services use versioned base paths: `/api/v1/...`.
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

- `Authorization: Bearer <jwt>` for authenticated routes
- `X-Request-Id: <uuid>` for tracing when the gateway does not inject one
- `Content-Type: application/json`
- `Accept: application/json`

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

Invalidates the current `jti` via blacklist storage.

### POST `/2fa/enable`

Returns QR provisioning data for TOTP setup.

### POST `/2fa/verify`

Verifies a TOTP code and activates the second factor.

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

Supports filter parameters such as `status`, `symbol`, `side`, `page`, and `size`.

### GET `/{orderId}`

Returns the current order state.

### DELETE `/{orderId}`

Cancels an open order.

### Error Notes

- Reject orders with invalid quantities, unsupported symbols, or insufficient balance.
- Price and quantity validation must run before the order is persisted.

---

## 6. Market Data Service

Base path: `/api/v1/market`

### GET `/prices`

Response:

```json
{
  "data": [
    {
      "symbol": "BTCUSDT",
      "price": "71234.12000000",
      "source": "BINANCE"
    }
  ]
}
```

### GET `/prices/{symbol}`

### GET `/history/{symbol}`

Query parameters:

- `interval`
- `limit`

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

- STOMP destination for live prices: `/topic/prices`
- Client subscription key should be symbol-aware where applicable

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

Returns historical P&L snapshots.

---

## 8. Notification Service

Base path: `/api/v1/notifications`

### GET `/me`

Returns notification history.

### POST `/preferences`

Updates notification preferences.

### Delivery Channels

- Kafka event consumer
- WebSocket push
- Email delivery where configured

---

## 9. Reporting Service

Base path: `/api/v1/reports`

### POST `/portfolio`

Request:

```json
{
  "userId": "c0d1d43c-0d9c-4a4d-8e2c-7ed1dbce7f56",
  "from": "2026-01-01T00:00:00Z",
  "to": "2026-06-24T00:00:00Z"
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

Returns report status and download URL when ready.

---

## 10. Gateway Contract

The gateway is the only public ingress point.

- JWT validation happens here.
- Rate limiting happens here.
- Service routing happens here.
- Gateway should expose only edge-facing routes, not business logic.

Suggested public routes:

- `/api/v1/auth/**`
- `/api/v1/users/**`
- `/api/v1/orders/**`
- `/api/v1/market/**`
- `/api/v1/portfolio/**`
- `/api/v1/notifications/**`
- `/api/v1/reports/**`

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

- Gateway enforces per-user request limits through Redis.
- Default public API quota: `60 requests/minute/user`.
- Auth endpoints may use stricter burst protection.
- WebSocket connections should also be counted for connection pressure where applicable.
- Rate limit keys follow the Redis rule `rate_limit:{user_id}`.

### Rate Limit Error Example

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

- Keep request and response DTOs versioned by API path, not by class name.
- Use `enum` values in API payloads only when stable and documented.
- Never expose persistence models directly.
- Prefer a single error envelope across all services.

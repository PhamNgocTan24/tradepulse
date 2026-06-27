# TradePulse - API Gateway Architecture

This document provides a detailed visual diagram and technical breakdown of the API Gateway (`api-gateway-service`) request flow in the TradePulse system, based on our implementations.

---

## 1. Request Flow Diagram

Below is the sequence diagram illustrating how a client request is received, processed by the filter pipeline, validated against security/caching policies, and routed downstream.

```mermaid
sequenceDiagram
    autonumber
    actor Client as Client (Web/Postman)
    participant GW as API Gateway (8080)
    participant DB_Redis as Redis Cache
    participant AuthSvc as Auth Service (8081)
    participant Downstream as Downstream Services

    Client->>GW: HTTP Request (e.g. POST /api/auth/login)
    
    Note over GW: 1. LoggingFilter (Highest Order)<br/>Logs incoming request metadata [-->]
    
    rect rgb(220, 230, 242)
        Note over GW: 2. SecurityConfig (Dual Filter Chain)
        alt Path is in PUBLIC_PATHS (e.g. /login, /register, swagger)
            Note over GW: Chain 1 (Order 1)<br/>Bypasses JWT validation filter
        else Protected Endpoints (e.g. /api/portfolio/**, /totp/setup)
            Note over GW: Chain 2 (Order 2)<br/>Validates JWT signature
            opt JWK Set is not cached
                GW->>AuthSvc: GET /.well-known/jwks.json
                AuthSvc-->>GW: Public JWK Set (RS256 keys)
            end
            Note over GW: Validates JWT signature.<br/>If invalid: returns 401 & terminates.
        end
    end

    rect rgb(230, 245, 230)
        Note over GW: 3. TokenBlacklistFilter (Order -100)
        opt Request has JWT Token
            GW->>DB_Redis: Check if key exists: blacklist:{jti}
            DB_Redis-->>GW: Yes/No
            alt Token is blacklisted (Logged out)
                GW-->>Client: HTTP 401 Unauthorized (Terminated)
            end
        end
    end

    rect rgb(255, 240, 230)
        Note over GW: 4. RateLimitFilter (Order -90)
        opt Request has JWT Token
            GW->>DB_Redis: Increment count for key: rate_limit:{userId} (TTL 60s)
            DB_Redis-->>GW: Current count
            alt Count > 60 requests/minute
                GW-->>Client: HTTP 429 Too Many Requests (Terminated)
            end
        end
    end

    Note over GW: 5. Routing Match (application.yml)<br/>Matches path pattern (e.g. /api/auth/**)
    GW->>Downstream: Proxies request to downstream service (e.g. auth-service:8081)
    Downstream-->>GW: Returns HTTP Response

    Note over GW: 6. LoggingFilter (doFinally)<br/>Logs response metadata & duration [<--]
    GW-->>Client: Stream Response to Client
```

---

## 2. Key Components Breakdown

### 1. `LoggingFilter` (WebFilter, `Ordered.HIGHEST_PRECEDENCE`)
*   **Role**: Access logging.
*   **Execution**: Operates before Spring Security. This guarantees that every request—whether successful, rate-limited, or blocked by security—is logged to stdout.
*   **Mechanism**: Captures the entry timestamp synchronously, passes execution downstream, and registers an asynchronous `.doFinally()` callback to measure response execution time (`ms`) and print the final request status.

### 2. `SecurityConfig` (Spring Security WebFilterChain)
*   **Role**: Endpoint access control.
*   **Dual Chain Mechanism**:
    1.  **Public Chain (`Order(1)`)**: Match requests configured in `PUBLIC_PATHS`. Does not apply any JWT resource server filters. Stale/expired Bearer tokens supplied in headers are ignored.
    2.  **Protected Chain (`Order(2)`)**: Handles all other requests. Mandates a valid JWT signature verified via the `jwk-set-uri` parameter.

### 3. `TokenBlacklistFilter` (GlobalFilter, `Order(-100)`)
*   **Role**: Block access from tokens belonging to logged-out sessions.
*   **Execution**: Triggered only for authenticated requests.
*   **Mechanism**: Queries Redis asynchronously using the token's unique ID (`jti`) as the key (`blacklist:<jti>`). If the key exists, the request is immediately aborted with `401 Unauthorized`.

### 4. `RateLimitFilter` (GlobalFilter, `Order(-90)`)
*   **Role**: Prevent API flooding (spams/DDOS).
*   **Execution**: Triggered only for authenticated requests.
*   **Mechanism**: Increments the key `rate_limit:<userId>` in Redis using atomic operations with a 60-second sliding expiration TTL. Denies requests exceeding 60 requests per minute with `429 Too Many Requests`.

### 5. Routing Router (Spring Cloud Gateway Engine)
*   **Role**: Dynamic packet forwarding.
*   **Mechanism**: Resolves route criteria statically loaded from `application.yml`'s `spring.cloud.gateway.routes` configuration. Proxies requests asynchronously through Netty's reactive HTTP client to the designated port.

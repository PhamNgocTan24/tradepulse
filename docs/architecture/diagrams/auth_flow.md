# TradePulse — Authentication & Security Flow

This diagram illustrates the login process, JWT token generation, downstream request validation, and the 2FA setup flow.

## 1. Authentication & Request Validation Flow

```mermaid
sequenceDiagram
    autonumber
    actor Client as Client App (React/Postman)
    participant Gateway as api-gateway-service (8080)
    participant Auth as auth-service (8081)
    participant Downstream as Downstream Service (e.g., user-service)
    participant DB as PostgreSQL (auth_db)
    participant Redis as Redis Cache

    Note over Client, Auth: Step 1: Login & Token Issuance
    Client->>Gateway: POST /api/v1/auth/login {email, password}
    Gateway->>Auth: Forward to /login
    Auth->>DB: Fetch user password_hash & 2FA status
    DB-->>Auth: User record
    Auth->>Auth: Validate BCrypt hash
    alt 2FA is enabled
        Auth-->>Client: Prompt 2FA Code
        Client->>Auth: POST /api/v1/auth/login {email, password, totp_code}
        Auth->>Auth: Verify TOTP secret using java-otp
    end
    Auth->>Auth: Generate RS256 JWT Access (15m) & Refresh (7d)
    Auth->>DB: Store hashed Refresh token
    Auth-->>Client: Return JWT access & refresh tokens (JSON)

    Note over Client, Downstream: Step 2: Request Validation (e.g., GET /api/v1/users/me)
    Client->>Gateway: GET /api/v1/users/me (Authorization: Bearer <token>)
    Gateway->>Redis: Check blacklist:jti
    alt Token is Blacklisted
        Redis-->>Gateway: Blacklisted!
        Gateway-->>Client: 401 Unauthorized
    else Token is Valid
        Redis-->>Gateway: Not blacklisted
    end
    
    alt GWKS Cache is empty or expired
        Gateway->>Auth: GET /.well-known/jwks.json
        Auth-->>Gateway: Public Key Set (kid, n, e)
        Note over Gateway: Cache JWKS keys locally
    end

    Gateway->>Gateway: Verify JWT Signature using RS256
    Gateway->>Gateway: Extract sub (user_id) & roles
    Gateway->>Downstream: Forward Request with X-User-Id: <user_id>
    Note over Downstream: Trust Gateway's validation. No re-verification.
    Downstream->>Downstream: Check @PreAuthorize roles
    Downstream-->>Client: 200 OK with User Profile Data
```

## 2. Token Logout & Blacklisting Flow

```mermaid
sequenceDiagram
    autonumber
    actor Client as Client App
    participant Gateway as api-gateway-service (8080)
    participant Auth as auth-service (8081)
    participant Redis as Redis Cache

    Client->>Gateway: POST /api/v1/auth/logout (Bearer <token>)
    Gateway->>Auth: Forward to /logout
    Auth->>Auth: Parse JWT & extract jti (Token ID) & expiry time (exp)
    Auth->>Redis: SET blacklist:<jti> "1" EXAT <exp>
    Redis-->>Auth: OK
    Auth-->>Client: 204 No Content
```

## 3. TOTP 2FA Provisioning Flow

```mermaid
sequenceDiagram
    autonumber
    actor Client as Client App
    participant Gateway as api-gateway-service (8080)
    participant Auth as auth-service (8081)
    participant DB as PostgreSQL (auth_db)

    Client->>Gateway: GET /api/v1/auth/totp/setup (Bearer <token>)
    Gateway->>Gateway: Verify JWT, extract sub (user_id)
    Gateway->>Auth: Forward with X-User-Id header
    Auth->>Auth: Generate random 160-bit Secret
    Auth->>Auth: Encrypt Secret with AES-256
    Auth->>DB: Save encrypted totp_secret (totp_enabled = FALSE)
    Auth->>Auth: Generate otpauth:// URL (Issuer: TradePulse)
    Auth->>Auth: Generate QR Code image bytes using ZXing
    Auth-->>Client: Return QR code bytes + secret key
    
    Client->>Gateway: POST /api/v1/auth/totp/confirm?code=123456
    Gateway->>Auth: Forward with X-User-Id header
    Auth->>DB: Fetch encrypted secret
    Auth->>Auth: Decrypt secret & verify code
    Auth->>DB: Set totp_enabled = TRUE
    Auth-->>Client: 204 No Content
```

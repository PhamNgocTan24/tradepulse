# TradePulse Folder Structure

This document defines where code should live in each module so the codebase stays predictable as new services are added.

It is written for the current scaffold phase: the repository has modules, but no Java source files yet.

For service behavior and API contracts, see [CLAUDE.md](CLAUDE.md) and [API_CONTRACTS.md](API_CONTRACTS.md).

---

## 1. Top-Level Layout

```text
tradepulse/
├── CLAUDE.md
├── README.md
├── API_CONTRACTS.md
├── DESIGN_PATTERNS.md
├── FOLDER_STRUCTURE.md
├── SYNTAX.md
├── pom.xml
├── services/
├── shared/
├── infrastructure/
├── docker/
└── .kiro/
```

### Meaning Of Top-Level Folders

- `services/` contains deployable microservices.
- `shared/` contains shared libraries that are intentionally cross-service.
- `infrastructure/` contains Terraform and Kubernetes manifests.
- `docker/` contains local infrastructure bootstrap files.
- `.kiro/` contains architecture and product specs.

---

## 2. Shared Modules

### `shared/common-dto`

Use this module for data contracts shared across service boundaries.

Put here:

- cross-service request/response records
- Kafka event payloads
- shared pagination or error envelope DTOs
- shared value objects that are truly stable across services

Do not put here:

- service-specific entities
- repository classes
- business logic
- constants that only one service uses

### `shared/common-util`

Use this module for functions, helpers, and constants that are truly shared across services.

Put here:

- reusable pure functions
- shared constants used by multiple services
- formatting helpers
- validation helpers
- date/time helpers that are not service-specific

Do not put here:

- one-off service helpers
- persistence code
- business orchestration
- anything that belongs to a specific service boundary

### `shared/common-security`

Use this module for shared security utilities.

Put here:

- JWT claim models
- token parsing helpers
- shared security constants
- authorization utilities that are identical across services

Do not put here:

- gateway-only filters
- auth-service token issuance logic
- service-specific roles or policy rules that are not shared

---

## 3. Service Module Standard Layout

Each service should follow the same high-level package shape:

```text
com.tradepulse.<service>/
├── <service>Application.java
├── controller/
├── service/
├── repository/
├── domain/
├── dto/
├── mapper/
├── event/
├── client/
├── config/
├── exception/
└── util/
```

### Package Responsibilities

- `controller/` for REST controllers and WebSocket entry points.
- `service/` for orchestration and transactional business logic.
- `repository/` for persistence access only.
- `domain/` for entities, aggregates, and value objects.
- `dto/` for request and response models.
- `mapper/` for MapStruct mappers.
- `event/` for Kafka event classes, producers, and consumers.
- `client/` for external API clients such as Binance or other service clients.
- `config/` for Spring configuration.
- `exception/` for domain and application exceptions.
- `util/` for small service-local helpers only when a shared abstraction would be overkill.

---

## 4. Domain Package Rules

### `domain/entity`

Use for JPA or MongoDB persistent objects.

### `domain/valueobject`

Use for immutable domain concepts such as:

- Money
- Symbol
- OrderId
- PortfolioValue

### `domain/enums`

Use for service-local enums that belong to the domain model, such as:

- `OrderStatus`
- `OrderSide`
- `OrderType`
- `NotificationType`

### `domain/model`

Use for aggregates or read models that are not direct database entities.

### Rule For Enums

- Put enums in `domain/enums` when they are part of business meaning.
- Put protocol enums in `dto` only when they are part of API contract.
- Do not scatter enums across `controller`, `service`, or `repository`.

---

## 5. Constants And Configuration

### `config/constants`

Use this for constants that are shared inside one service but not across the whole repo.

Examples:

- Redis key prefixes
- Kafka topic names used by one service
- websocket destinations
- validation limits

### `shared/common-dto` Or `shared/common-security`

Use shared modules only when the constant is genuinely cross-service and stable.

Examples:

- header names
- shared claim names
- error codes used across services

### Rule For Constants

- Do not create a `constants` dumping ground.
- If a constant is only used in one package, keep it near that package.
- If a constant is used by many unrelated services, move it to a shared module.
- If a function is used by multiple services and is not business-specific, move it to a shared module too.

---

## 6. Common Functions And Reusable Code

### `util/`

Use only for small local helpers that are not worth a dedicated abstraction.

### `client/`

Use for external-system adapters and service-to-service callers.

### `mapper/`

Use for conversion logic between DTOs, entities, and domain models.

### `domain/service/`

Use when a reusable domain rule belongs to the business model rather than orchestration.

### Rule For Common Functions

- If the function is used by one service only, keep it in that service.
- If it is pure business mapping or rule logic, prefer `domain` or `mapper`.
- If it is truly shared and stable, move it to `shared/common-util` or another shared module.
- Avoid a generic `utils` package at the root level.

---

## 7. Recommended Folder Shape Per Service

### `auth-service`

```text
com.tradepulse.auth/
├── controller/
├── service/
├── repository/
├── domain/
├── dto/
├── mapper/
├── config/
├── exception/
└── security/
```

### `order-service`

```text
com.tradepulse.order/
├── controller/
├── service/
├── repository/
├── domain/
├── dto/
├── mapper/
├── event/
├── outbox/
├── config/
└── exception/
```

### `market-data-service`

```text
com.tradepulse.marketdata/
├── websocket/
├── client/
├── service/
├── repository/
├── domain/
├── dto/
├── mapper/
├── event/
├── config/
└── exception/
```

### `matching-engine`

```text
com.tradepulse.matching/
├── engine/
├── model/
├── service/
├── event/
├── config/
└── health/
```

### `portfolio-service`

```text
com.tradepulse.portfolio/
├── controller/
├── service/
├── repository/
├── domain/
├── dto/
├── mapper/
├── event/
├── projection/
├── config/
└── exception/
```

---

## 8. Where Specific Things Go

### Enums

- API enums that are part of request/response go in `dto`.
- Business enums go in `domain/enums`.
- Shared protocol enums go in `shared/common-dto` only if multiple services need them.

### Constants

- Service-local constants go near the feature they support, often under `config` or a feature package.
- Global constants go in shared modules only when more than one service depends on them.

### Common Functions

- Mapping and transformation functions go in `mapper`.
- External client helpers go in `client`.
- Business rules go in `service` or `domain`.
- Avoid putting unrelated helpers in one big `util` package.

### Exceptions

- Domain exceptions go in `exception`.
- Validation errors should be handled centrally via `@RestControllerAdvice`.

### Kafka Code

- Producers go in `event` or a dedicated `producer` package.
- Consumers go in `event` or a dedicated `consumer` package.
- Event payload classes should stay in `dto` or `event`, not in `domain/entity`.

### WebSocket Code

- Gateway or service socket entry points go in `websocket`.
- Message handlers belong near the feature they support.

---

## 9. Example Package Decision Table

| Item | Put It Here | Reason |
|---|---|---|
| `OrderStatus` enum | `domain/enums` | Business meaning |
| `AuthHeaderNames` constant | `shared/common-security` | Shared security rule |
| `DateFormats` helper | `shared/common-util` | Shared helper used across services |
| `price:BTCUSDT` key builder | `market-data-service/config` | Service-local cache rule |
| `Money` value object | `domain/valueobject` | Domain concept |
| `OrderResponse` | `dto/response` | API contract |
| `OrderMapper` | `mapper` | Conversion logic |
| `BinanceWebSocketClient` | `client` or `websocket` | External integration |
| `JwtUtils` | `shared/common-security` | Shared security helper |
| `OrderNotFoundException` | `exception` | Domain error |

---

## 10. Folder Creation Rule

- Add a new folder only when it represents a real architectural boundary.
- Do not create folders just because a class feels lonely.
- Prefer a narrow, predictable package tree over many ad hoc folders.
- If a new concept appears in more than one place, document it here before duplicating it.

### Shared Module Rule

- If a helper function or constant is reused across services, place it in `shared/`.
- Prefer `shared/common-util` for general helpers and constants.
- Prefer `shared/common-dto` for cross-service contract objects.
- Prefer `shared/common-security` for shared auth and security concerns.

# TradePulse Folder Structure

> This document defines where code should live in each module so the codebase stays predictable as new services are added.
>
> For service behavior and API contracts, see [Architecture.md](../architecture/Architecture.md) and [ApiContracts.md](../api/ApiContracts.md).
> For implementation patterns, see [Patterns.md](Patterns.md) and [Syntax.md](Syntax.md).

---

## 1. Top-Level Layout

```text
tradepulse/
├── CLAUDE.md                  # AI agent context — commands, rules, references
├── AGENTS.md                  # AI agent context (identical to CLAUDE.md)
├── README.md                  # Quick start
├── API_CONTRACTS.md           # Root copy (source of docs/api/ApiContracts.md)
├── DESIGN_PATTERNS.md         # Root copy (source of docs/development/Patterns.md)
├── FOLDER_STRUCTURE.md        # Root copy (source of docs/development/FolderStructure.md)
├── SYNTAX.md                  # Root copy (source of docs/development/Syntax.md)
├── pom.xml                    # Maven multi-module root
├── services/                  # Deployable microservices
├── shared/                    # Shared libraries (cross-service)
├── infrastructure/            # Terraform + Kubernetes manifests
├── docker/                    # Local dev docker-compose
├── scripts/                   # Operational scripts
│   ├── start-services.sh      # Start/stop/status all services
│   ├── logs/                  # Service log files (git-ignored)
│   └── pids/                  # Service PID files (git-ignored)
├── docs/                      # Structured documentation
│   ├── architecture/
│   ├── development/
│   ├── api/
│   ├── planning/
│   └── operations/
└── .kiro/                     # Architecture and product specs
    └── specs/tradepulse.md
```

### Meaning of Top-Level Folders

- `services/` — deployable microservices, each with its own Maven module
- `shared/` — shared libraries intentionally cross-service
- `infrastructure/` — Terraform and Kubernetes manifests
- `docker/` — local infrastructure bootstrap files
- `scripts/` — operational scripts (start, seed, setup)
- `docs/` — structured documentation (see [docs structure](#docs-structure) below)
- `.kiro/` — architecture and product specs

---

## 2. Docs Structure

```text
docs/
├── architecture/
│   └── Architecture.md        # System design, service catalogue, DB schema, Kafka, Security, AWS
├── development/
│   ├── Patterns.md            # 15 distributed-systems patterns
│   ├── Syntax.md              # 15 Java bad/good examples
│   └── FolderStructure.md     # This file — package and folder conventions
├── api/
│   └── ApiContracts.md        # REST contracts, error codes, rate limiting
├── planning/
│   ├── backlog.md             # Product Backlog: Functional & Non-Functional requirements
│   ├── roadmap.md             # Milestones & implementation roadmap
│   └── tasks.md               # Active developer tasks + running services
└── operations/
    └── Runbook.md             # Local dev setup, start/stop, logs, troubleshooting
```

---

## 3. Shared Modules

### `shared/common-dto`

Use this module for data contracts shared across service boundaries.

**Put here:**
- Cross-service request/response records
- Kafka event payload classes
- Shared pagination or error envelope DTOs
- Shared value objects that are truly stable across services

**Do not put here:**
- Service-specific entities
- Repository classes
- Business logic
- Constants that only one service uses

### `shared/common-security`

Use this module for shared security utilities.

**Put here:**
- JWT claim models
- Token parsing helpers
- Shared security constants
- Authorization utilities identical across services

**Do not put here:**
- Gateway-only filters
- auth-service token issuance logic
- Service-specific roles or policy rules

---

## 4. Service Module Standard Layout

Each service follows the same high-level package shape:

```text
com.tradepulse.<service>/
├── <Service>Application.java
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

| Package | Purpose |
|---|---|
| `controller/` | REST controllers and WebSocket entry points |
| `service/` | Orchestration and transactional business logic |
| `repository/` | Persistence access only |
| `domain/` | Entities, aggregates, and value objects |
| `dto/` | Request and response models |
| `mapper/` | MapStruct mappers |
| `event/` | Kafka event classes, producers, and consumers |
| `client/` | External API clients (Binance, other service clients) |
| `config/` | Spring configuration classes |
| `exception/` | Domain and application exceptions |
| `util/` | Small service-local helpers only |

---

## 5. Domain Package Rules

### `domain/entity`

Use for JPA or MongoDB persistent objects.

### `domain/valueobject`

Use for immutable domain concepts such as:
- `Money`
- `Symbol`
- `OrderId`
- `PortfolioValue`

### `domain/enums`

Use for service-local enums belonging to the domain model:
- `OrderStatus`
- `OrderSide`
- `OrderType`
- `NotificationType`

### `domain/model`

Use for aggregates or read models that are not direct database entities.

### Rule for Enums

- Put enums in `domain/enums` when they carry business meaning
- Put protocol enums in `dto` only when part of API contract
- Do not scatter enums across `controller`, `service`, or `repository`

---

## 6. Constants and Configuration

### `config/constants`

Use for constants shared inside one service but not across the whole repo.

Examples:
- Redis key prefixes
- Kafka topic names used by one service
- WebSocket destinations
- Validation limits

### Shared modules

Use shared modules only when the constant is genuinely cross-service and stable.

Examples:
- Header names
- Shared claim names
- Error codes used across services

### Rule for Constants

- Do not create a `constants` dumping ground
- If a constant is only used in one package, keep it near that package
- If a constant is used by many unrelated services, move it to a shared module

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
├── websocket/         # BinanceWebSocketClient + STOMP handlers
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
├── engine/            # OrderBook, matching algorithm (DSA hot path)
├── model/             # Order, OrderBook, VWAP window
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

### `notification-service`

```text
com.tradepulse.notification/
├── controller/
├── service/
├── repository/
├── domain/
├── dto/
├── event/
├── config/
│   └── MailConfig.java   # AWS SES client (mock for local dev)
└── exception/
```

### `reporting-service`

```text
com.tradepulse.reporting/
├── controller/
├── service/
├── repository/
├── domain/
├── dto/
├── client/            # S3 client, Lambda invoker
├── config/
└── exception/
```

---

## 8. Where Specific Things Go

### Enums

- API enums (part of request/response) → `dto`
- Business enums → `domain/enums`
- Shared protocol enums → `shared/common-dto` only if multiple services need them

### Constants

- Service-local constants → near the feature they support, often under `config`
- Global constants → shared modules only when more than one service depends on them

### Common Functions

- Mapping and transformation → `mapper`
- External client helpers → `client`
- Business rules → `service` or `domain`
- Avoid putting unrelated helpers in one big `util` package

### Exceptions

- Domain exceptions → `exception`
- Validation errors → handled centrally via `@RestControllerAdvice`

### Kafka Code

- Producers → `event` or dedicated `producer` package
- Consumers → `event` or dedicated `consumer` package
- Event payload classes → `dto` or `event`, not in `domain/entity`

### WebSocket Code

- Gateway or service socket entry points → `websocket`
- Message handlers → near the feature they support

---

## 9. Package Decision Table

| Item | Put It Here | Reason |
|---|---|---|
| `OrderStatus` enum | `domain/enums` | Business meaning |
| `AuthHeaderNames` constant | `shared/common-security` | Shared security rule |
| `DateFormats` helper | `shared/common-util` | Shared helper across services |
| `price:BTCUSDT` key builder | `market-data-service/config` | Service-local cache rule |
| `Money` value object | `domain/valueobject` | Domain concept |
| `OrderResponse` | `dto/response` | API contract |
| `OrderMapper` | `mapper` | Conversion logic |
| `BinanceWebSocketClient` | `client` or `websocket` | External integration |
| `JwtUtils` | `shared/common-security` | Shared security helper |
| `OrderNotFoundException` | `exception` | Domain error |
| `OutboxEvent` | `outbox` | Outbox pattern entity |
| `EventLog` | `event` | Idempotency deduplication record |

---

## 10. Folder Creation Rule

- Add a new folder only when it represents a real architectural boundary
- Do not create folders just because a class feels lonely
- Prefer a narrow, predictable package tree over many ad hoc folders
- If a new concept appears in more than one place, document it here before duplicating it

### Shared Module Rule

- If a helper function or constant is reused across services → place it in `shared/`
- Prefer `shared/common-dto` for cross-service contract objects
- Prefer `shared/common-security` for shared auth and security concerns

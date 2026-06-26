# TradePulse

TradePulse is a scaffold-phase crypto trading platform built around real-time Binance market data, Kafka-driven services, and polyglot persistence.

## Documentation

- [CLAUDE.md](CLAUDE.md) - compact project memory and working context
- [Syntax Guide](docs/development/Syntax.md) - concrete implementation rules and code examples
- [Design Patterns](docs/development/Patterns.md) - service and distributed-systems patterns
- [API Contracts](docs/api/ApiContracts.md) - public API contracts and error model
- [Folder Structure](docs/development/FolderStructure.md) - package and folder conventions
- [.kiro/specs/tradepulse.md](.kiro/specs/tradepulse.md) - longer architecture specification

## Quick Start

```bash
cd docker && docker-compose up -d
mvn spring-boot:run -pl services/auth-service
```

## Build And Test

```bash
mvn clean install -DskipTests
mvn clean install
mvn test
```

## Project Shape

- Java 21
- Spring Boot 3.2.5
- Spring Cloud 2023.0.1
- Kafka 3.6
- PostgreSQL, MongoDB, Redis

## Working Rules

- Each service owns its database.
- Use Kafka or REST for service-to-service communication.
- Keep monetary values in `BigDecimal`.
- Keep audit logs append-only.
- Use deterministic Kafka keys and Redis key prefixes.

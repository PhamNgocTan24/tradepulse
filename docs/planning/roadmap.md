# TradePulse — Release Roadmap & Milestones

This document describes the high-level phases, epics, and tech stack details of the TradePulse project. For active developer tasks, see [tasks.md](tasks.md). For detailed requirements, see [backlog.md](backlog.md).

---

## 1. Implementation Roadmap

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

## 2. Tech Stack Summary

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

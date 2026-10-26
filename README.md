# ClaimStream

A distributed healthcare **claims adjudication platform**, built from scratch one milestone at a time to learn
how large-scale, multi-tier, event-driven systems are designed, built, tested and operated.

A claim flows through **submission → eligibility → pricing → adjudication → payment → remittance**.
By the end, ClaimStream does that across independent services over Kafka and gRPC, with idempotency,
sagas, circuit breakers, fraud scoring, caching and full observability.

## Quick start

Prerequisites: JDK 21, Docker Desktop running.

```bash
./mvnw verify                 # Checkstyle + unit + integration tests (Testcontainers)
docker compose up -d --build  # Postgres, Redis, Kafka + Intake Service
curl -s localhost:8080/actuator/health
curl -s localhost:8080/api/v1/platform
docker compose down
```

| Service | Port |
|---|---|
| Intake Service | 8080 |
| PostgreSQL (`claimstream` / `claimstream`) | 5432 |
| Redis | 6379 |
| Kafka (host listener) | 9092 |

## Repository layout

```
claimstream/
├── pom.xml                  # parent POM: Java 21, Spring Boot 3.5, Checkstyle
├── claims-domain/           # plain-Java domain model: Money, Member, Claim...
├── intake-service/          # front door for claims (more services from M5)
├── config/checkstyle/       # code style rules
├── docker-compose.yml       # local stack
├── .github/workflows/ci.yml # CI: style → compile → tests → image
└── docs/
    ├── LEARNING.md          # study guide, one section per milestone
    ├── lessons/             # full milestone lessons
    └── adr/                 # architecture decision records
```

## Milestones

| # | Topic | Status |
|---|---|---|
| M0 | Big picture & setup | ✅ |
| M1 | Domain model (OOD) | ✅ |
| M2 | Adjudication rules engine (design patterns) | |
| M3 | Batch intake (multi-threading) | |
| M4 | Idempotency & concurrency correctness | |
| M5 | Event-driven services (Kafka, outbox) | |
| M6 | Reliability (timeouts, retries, circuit breaker, gRPC) | |
| M7 | Distributed transactions (saga) | |
| M8 | Fraud detection | |
| M9 | Scaling & performance | |
| M10 | Observability & operations | |
| M11 | SDLC wrap-up | |

See [`docs/LEARNING.md`](docs/LEARNING.md) for the study guide.

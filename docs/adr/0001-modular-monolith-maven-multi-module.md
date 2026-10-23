# ADR-0001: Start as a modular monolith in one Maven multi-module repo

- **Status:** Accepted
- **Date:** 2026-10-23
- **Milestone:** M0

## Context

ClaimStream will end up as five or more services (Intake, Eligibility, Pricing, Adjudication, Payment, Fraud)
talking over Kafka and gRPC. On day one, though, we have no proven domain boundaries, one developer, and a
requirement that everything builds and runs locally with one command.

## Decision

- One Git repository (monorepo) with a **Maven parent POM** and one module per future deployable or shared library.
- The parent inherits from `spring-boot-starter-parent`, so every module gets the same, tested library versions.
- Start with a single module, `intake-service`. Add modules only when a milestone needs them
  (domain model in M1, separate services in M5).
- Code-style rules (Checkstyle) and the Testcontainers version live in the parent, so every module follows them.

## Alternatives considered

| Option | Why not (yet) |
|---|---|
| **One repo per service (polyrepo)** | Cross-service changes need several PRs and version bumps; heavy for one developer; slows the early refactoring we expect in M1–M4. |
| **Microservices from day one** | Pays the full cost of distribution (network failures, eventual consistency, deployment overhead) before we know where the seams are. Risk of a *distributed monolith*. |
| **Single-module Spring Boot app** | Simplest, but nothing stops packages from depending on each other; splitting later is harder. Modules make boundaries compile-time enforced. |
| **Gradle** | Equally capable; Maven chosen for its declarative POM, which is easier to read while learning and common in enterprise Java shops. |

## Consequences

- ✅ One command builds and tests everything: `./mvnw verify`.
- ✅ Atomic changes across modules in one PR.
- ✅ Module boundaries prepare the M5 split: extracting a service is moving a module, not untangling packages.
- ❌ CI builds everything on every change (fine at this size; can add `-pl`/affected-module builds later).
- ❌ Everyone shares one dependency set from the Boot BOM; a module that needs a different version must override explicitly.

# ADR-0002: Immutable domain model with records, typed ids and BigDecimal money

- **Status:** Accepted
- **Date:** 2026-10-26
- **Milestone:** M1

## Context

Every service will share one vocabulary: Member, Provider, Plan, Claim, ClaimLine and money amounts. Those objects will be
read concurrently by many threads (M3), serialized into Kafka messages (M5) and cached (M9). Money errors and mixed-up ids
in a claims system become real overpayments and HIPAA-relevant data errors.

## Decision

1. Domain types live in a separate `claims-domain` module with **no framework dependencies**.
2. Types are **Java records** with validating compact constructors. Collections are copied with `List.copyOf`.
3. Changes return new objects (`Member.withDeductibleApplied`), never setters.
4. Each identifier gets its own type: `MemberId`, `Npi`, `ClaimId`.
5. Money is a `Money` record over `BigDecimal`, scale 2, `HALF_EVEN` rounding, never negative.
6. Entities (`Member`, `Provider`, `Claim`) override `equals`/`hashCode` to compare by id; value objects keep record equality.
7. `Claim` references its member and provider **by id**, not by embedding them.

## Alternatives considered

| Option | Why not |
|---|---|
| Mutable JavaBeans with getters/setters | Any code can create invalid or half-updated objects; not thread-safe without locks |
| Lombok `@Value` / `@Builder` | Works, but adds a compile-time dependency and hides code we want to read while learning; records cover our needs |
| `double` or `long` cents for money | `double` drifts; `long` cents works but loses currency semantics and rounding rules; `BigDecimal` is explicit |
| `String` ids | Primitive obsession: arguments can be swapped silently; validation scattered |
| JPA entities as the domain model | Couples the domain to the database and forces mutability (no-arg constructors, setters); persistence mapping comes later, separately |
| Embedding `Member` inside `Claim` | Stale copies of accumulators; larger messages; unclear ownership |

## Consequences

- ✅ Invalid objects can't exist; downstream code doesn't re-validate.
- ✅ Thread-safe and cache-safe by construction.
- ✅ Domain tests run in milliseconds with no Spring context or Docker.
- ❌ Records override `equals` per field by default, so entities must remember to override it (we test this).
- ❌ Persistence (M4/M5) needs a mapping layer between records and tables.
- ❌ "Wither" methods allocate new objects; negligible at our scale.

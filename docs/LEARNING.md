# ClaimStream Study Guide

One section per milestone: the idea, the diagram, the cards. Full lessons (walkthroughs, interview
questions, quizzes) live in [`docs/lessons/`](lessons/). Design decisions live in [`docs/adr/`](adr/).

**Running example:** Maria Lopez (`M-1001`, `GOLD-PPO`, $500 deductible, $350 met) visits Dr. Patel Family Clinic
(NPI `1234567890`, in-network). Claim `CLM-5001`: CPT `99213`, billed $150, allowed $110.

| Milestone | Topic | Lesson |
|---|---|---|
| M0 | Big picture, multi-tier architecture, repo setup | [M0-big-picture.md](lessons/M0-big-picture.md) |
| M1 | Domain model, entities vs value objects, immutability, SOLID | [M1-domain-model.md](lessons/M1-domain-model.md) |

---

## M0 – Big Picture & Setup

**Summary.** A payer turns a provider's claim into a payment decision in six stages: submission → eligibility →
pricing → adjudication → payment → remittance. We'll build that as ClaimStream, starting as a **modular monolith**
(one Maven multi-module repo) organised in three tiers: **presentation** (talk to the outside world),
**service** (apply the rules), **data** (Postgres, Redis, Kafka). M0 ships the skeleton: one `intake-service`
module, Docker Compose for the data tier, Checkstyle, Testcontainers tests and GitHub Actions CI.

**The payer pipeline**

```mermaid
flowchart LR
    P[Provider] -->|837| S[Submission] --> E[Eligibility] --> PR[Pricing] --> A[Adjudication]
    A --> PAY[Payment] --> R[Remittance 835 / EOB]
    A -.->|deny| R
    A -.->|pend| Q[Manual review]
```

**The three tiers in our code**

```mermaid
flowchart TB
    subgraph Presentation
        C[PlatformInfoController<br/>Actuator /health]
    end
    subgraph Service
        B[Spring Boot app<br/>rules engine from M2]
    end
    subgraph Data
        PG[(PostgreSQL)]
        RD[(Redis)]
        K[[Kafka]]
    end
    C --> B --> PG
    B --> RD
    B -.->|from M5| K
```

**CLM-5001 math** (computed for real in M2): allowed $110, deductible remaining $150 → all $110 goes to the deductible
→ plan pays 80% × $0 = **$0**, Maria owes **$110**, provider writes off $40.

**Remember cards**
- **Payer pipeline** = submission → eligibility → pricing → adjudication → payment → remittance.
- **Allowed amount** = contract price; billed − allowed = in-network write-off the member never pays.
- **Deductible first, then coinsurance**: plan pays coinsurance % only on what's left after the deductible.
- **Multi-tier** = presentation (talk) / service (think) / data (remember); each tier calls only the one below.
- **Modular monolith first**: split into services only when a boundary is proven and needs to scale independently.
- **Testcontainers** = real Postgres/Redis in Docker for tests; never trust H2 for concurrency behaviour.
- **CI** = fail fast and cheap first: style → compile → unit → integration → image.
- **Maven Wrapper (`mvnw`)** = same build tool version on every laptop and in CI.

---

## M1 – Domain Model (OOD)

**Summary.** A new `claims-domain` module describes the business in plain Java with no framework. **Entities**
(`Member`, `Provider`, `Claim`) are identified by id; **value objects** (`Money`, `Npi`, `MemberId`, `ClaimId`, `Plan`,
`CoveragePeriod`, `ClaimLine`) are defined by their values. Everything is an immutable `record` that validates itself in its
constructor, so an invalid object can't exist. Money is `BigDecimal` at 2 decimals, never negative. A claim refers to its member
and provider by id, never by embedding them. SOLID shows up as small single-purpose types and a domain that depends on nothing;
Open/Closed and Interface Segregation get real examples in M2.

**Class diagram**

```mermaid
classDiagram
    direction LR
    class Money { <<value object>> +BigDecimal amount +plus() +minus() +times() +min() }
    class Plan { <<value object>> +String code +Money deductible +BigDecimal planShare }
    class CoveragePeriod { <<value object>> +LocalDate start +LocalDate end +covers(date) }
    class Member { <<entity>> +MemberId id +Money deductibleMet +deductibleRemaining() +withDeductibleApplied() }
    class Provider { <<entity>> +Npi npi +boolean inNetwork }
    class Claim { <<entity>> +ClaimId id +LocalDate dateOfService +totalBilled() }
    class ClaimLine { <<value object>> +int lineNumber +String cptCode +Money billed }
    Member --> Plan
    Member --> CoveragePeriod
    Claim --> Member : by MemberId
    Claim --> Provider : by Npi
    Claim "1" *-- "1..*" ClaimLine
    ClaimLine --> Money
```

**CLM-5001 with the building blocks:** write-off $150 − $110 = $40 · to deductible min($110, $150) = $110 ·
plan pays 80% × $0 = $0 · Maria pays $110 · her deductible met becomes $460 (as a new `Member`; the old one still says $350).

**Remember cards**
- **Entity** = identity matters (Maria, M-1001); equality by id.
- **Value object** = only values matter ($150); immutable; equality by value.
- **Never `double` for money**: `BigDecimal`, fixed scale, explicit rounding, wrapped in `Money`.
- **Validate in the constructor**: an invalid object should be impossible to create.
- **Defensive copy**: `List.copyOf` so callers can't change your internals.
- **Immutable = thread-safe by construction**; change by returning a new object.
- **Typed ids beat Strings**: `MemberId` and `Npi` can't be swapped by accident.
- **Refer across aggregates by id**, not by embedding objects: smaller messages, no stale copies.

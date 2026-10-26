# ADR-0003: Rules engine built from Chain of Responsibility, Strategy, Factory and State

- **Status:** Accepted
- **Date:** 2026-10-26
- **Milestone:** M2

## Context

Adjudication combines many independent decisions (eligibility, duplicates, medical necessity, fraud signals), several pricing
methods, benefit math and a status lifecycle. Payer rules change often. The engine will later run inside a Kafka consumer (M5) and
call remote services (M6), so it must not be tied to any infrastructure.

## Decision

1. A separate `adjudication-engine` module, plain Java, depending only on `claims-domain`.
2. **Chain of Responsibility** for edits: `AdjudicationRule` (one method) returning `RuleOutcome` CONTINUE / DENY / PEND with a
   reason code; `RuleChain` stops at the first non-continue. Implemented as an ordered list rather than linked handlers: simpler
   to read and reorder, same behaviour.
3. **Strategy** for pricing (`FeeSchedulePricing`, `PercentOfBilledPricing`) chosen by a **Factory** (`PricingStrategyFactory`)
   from the provider's network status.
4. **State** for the lifecycle: `ClaimStatus` constants declare their legal next states; `ClaimLifecycle` is an immutable history.
5. External data behind **ports** (`MemberDirectory`, `ProviderDirectory`, `FeeSchedule`, `ClaimHistory`). A failed eligibility
   lookup pends the claim; it never denies it.
6. Pricing and benefits are engine steps after the chain, not chain links, because they produce values the next step needs
   (allowed amount, breakdown) and map one-to-one to the PRICED and ADJUDICATED states.
7. M2 moves ADJUDICATED straight to PAID; the payment saga (M7) replaces that step.

## Alternatives considered

| Option | Why not |
|---|---|
| One procedural `adjudicate()` method | Fast to write, but every new rule edits it; rules can't be tested alone |
| A rules-engine product (Drools, Easy Rules) | Powerful for business-authored rules, but adds a DSL and runtime to learn; our rules are few and code-reviewed |
| Classic linked-list CoR (`setNext`) | Same pattern; harder to reorder and to see the whole chain at a glance |
| Status as a plain enum with `switch` checks | Transition rules scattered across callers; easy to set an illegal status |
| Pricing as chain rules writing into a mutable context | Hides data flow in shared mutable state; harder to reason about and test |

## Consequences

- ✅ New edits and pricing methods are new classes; the engine stays small (Open/Closed).
- ✅ Every rule, strategy and transition is unit-tested in isolation; scenario tests run all sample claims in memory.
- ✅ Ports let M4–M6 swap in Postgres, Redis and gRPC without touching business logic.
- ❌ More small classes to navigate; the chain wiring in `withStandardRules` is the map.
- ❌ Rule order is a hidden dependency (cheap rules first); documented and covered by tests.
- ❌ `ClaimHistory` duplicate lookup is in-memory here; at scale it needs an index and the race-safe idempotency of M4.

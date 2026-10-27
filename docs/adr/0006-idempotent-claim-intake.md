# ADR-0006: Idempotent claim intake with Redis SETNX and Postgres unique constraints

- **Status:** Accepted
- **Date:** 2026-10-27
- **Milestone:** M4

## Context

Providers' billing systems retry on timeouts, sometimes many times in parallel. Storing a claim twice can mean paying
it twice. The intake API must give an exactly-once *effect* for each logical request, under real concurrency, and keep
working if Redis is unavailable.

## Decision

1. `POST /api/v1/claims` requires an `Idempotency-Key` header (1–128 chars), chosen by the client and reused on retries.
2. **Redis `SET idem:<key> IN_PROGRESS NX EX 30`** is a fast gate: one caller per key proceeds; others get the stored
   answer or `409 REQUEST_IN_PROGRESS`. The finished answer is cached for 24 h. If Redis is down, callers are let through.
3. **Postgres is the guarantee.** The claim and the key row are inserted in one transaction; `claims.claim_id` and
   `idempotency_keys.idempotency_key` are primary keys. A losing INSERT waits for the winner to commit, then fails, and
   the winner's stored answer is replayed.
4. The response is stored with a SHA-256 hash of the request body. Same key + same body → replay the original status
   and body with `Idempotent-Replayed: true`. Same key + different body → `422 IDEMPOTENCY_KEY_REUSED`.
   Same claim id under a new key → `409 DUPLICATE_CLAIM`.
5. Errors use RFC 9457 `ProblemDetail` with a machine-readable `code`.
6. Schema is managed by Flyway migrations.

## Alternatives considered

| Option | Why not |
|---|---|
| Redis only | Keys expire or vanish on failover, and Redis isn't in the claim's transaction |
| Postgres only | Correct, and the fallback we keep; but every duplicate opens a transaction and contends on the row |
| Check-then-insert in Java | Race between check and insert; proven by `RaceConditionTest` to store duplicates |
| `SELECT ... FOR UPDATE` on a key row | Works, but holds locks and blocks callers; a unique constraint does the same job for "create once" |
| Dedupe on claim id only, no key | Can't tell a retry from a genuine resubmission or catch "same key, different body" |

## Consequences

- ✅ 50 simultaneous CLM-5002 requests store exactly one claim, with or without Redis (tested).
- ✅ Retries get a byte-identical answer, even after the Redis TTL, from Postgres.
- ❌ If a server dies while holding a key, retries get 409 until the 30 s in-progress TTL expires. A request that
  legitimately runs longer than 30 s could let a second caller in; Postgres still keeps one claim, but the TTL must
  stay above the slowest request.
- ❌ `idempotency_keys` grows forever; a cleanup job after the retry window is needed in production.
- ❌ Downstream consumers (M5, Kafka) still need their own idempotency because messaging is at-least-once.

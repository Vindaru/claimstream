# ADR-0007: Kafka between services, with a transactional outbox and idempotent consumers

- **Status:** Accepted
- **Date:** 2026-11-13
- **Milestone:** M5

## Context

Intake (M4) and the rules engine (M2) must be connected. A synchronous call would couple their speed and
availability, and the target is 10k claims a minute with bursts from nightly batch files. Writing to Postgres and
Kafka from one service is a dual write with no shared transaction. Kafka and any relay deliver at least once.

## Decision

1. Services communicate through Kafka topics: `claims.received` (6 partitions, key = member id),
   `claims.adjudicated` (6 partitions, key = member id), `claims.received.DLT`.
2. **Transactional outbox** (`event-outbox` module): an event is inserted into an `outbox` table in the same
   transaction as the business rows. `OutboxWriter.append` requires an existing transaction (`MANDATORY`).
3. A **polling relay** publishes unsent rows every 200 ms in batches of 500 using `FOR UPDATE SKIP LOCKED`, waits for
   all acks (producer `acks=all`, idempotence on), then sets `published_at`. Failures leave rows unsent for the next
   tick.
4. A new **Adjudication Service** consumes `claims.received` with 3 listener threads. It is an **idempotent
   consumer**: it checks `adjudications` for the claim id, runs the engine, and saves the verdict, the history row and
   the `ClaimAdjudicated` outbox row in one transaction. The primary key on `adjudications.claim_id` is the final guard.
5. Errors: 2 retries 500 ms apart, then the dead-letter topic. JSON and domain-validation errors skip retries.
6. Each service owns its tables: intake in `public`, adjudication in schema `adjudication`, on one Postgres server.

## Alternatives considered

| Option | Why not (now) |
|---|---|
| Synchronous REST call intake → engine | Couples latency and uptime; a slow rule slows every provider |
| Send to Kafka after commit, no outbox | Crash between commit and send loses the event |
| Kafka transactions (exactly-once) | Covers Kafka-to-Kafka; our side effects are in Postgres, so we still need idempotency |
| CDC with Debezium | Lower latency, but adds Kafka Connect to run; revisit at M9 if polling load shows up |
| SQS instead of Kafka | No replay, no consumer groups to learn; used on AWS in Phase 5 behind the same events |

## Consequences

- Intake answers without waiting for adjudication; the two deploy and scale separately.
- Delivery is at least once; every consumer must be idempotent (documented in each listener).
- Up to ~200 ms extra latency from polling.
- Deductibles are still in the Adjudication Service's memory (`ReferenceData`); M6/M7 fix that.
- New things to monitor: outbox backlog (`published_at IS NULL`), consumer lag, DLT size.

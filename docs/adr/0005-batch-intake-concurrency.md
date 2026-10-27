# ADR-0005: Batch intake with a bounded producer-consumer pipeline and virtual threads

- **Status:** Accepted
- **Date:** 2026-10-26
- **Milestone:** M3

## Context

Clearinghouses send claims as large batch files. Each claim needs an eligibility lookup, which is network or database
I/O. Single-threaded intake of 100k claims took ~19 s with a 100 µs lookup; with real latencies it would take hours.
The result must be exact: no claim lost or counted twice, whatever the threading.

## Decision

1. A separate `batch-intake` module. The per-line work (`ClaimIntake`: parse → M2 `EligibilityRule` → tally) is
   stateless and shared by every pipeline; pipelines are a Strategy that only decides threading.
2. **Thread pool pipeline:** one producer reading the file into a **bounded** `ArrayBlockingQueue` (capacity 1,000),
   a fixed pool of workers, poison pills for shutdown. The bound provides backpressure.
3. **Virtual thread pipeline:** one virtual thread per line, capped by a `Semaphore` (1,000 in flight).
   This is the recommended default for I/O-bound intake on Java 21: no pool size to tune.
4. Shared state is lock-free and thread-safe: `ConcurrentHashMap` for the roster and report, `LongAdder` counters.
5. A failing line never deadlocks the pipeline: workers keep draining, and the first failure is rethrown at the end.
   Malformed lines are counted, not fatal.
6. Every pipeline is tested to produce identical, exactly predicted counts.

## Alternatives considered

| Option | Why not |
|---|---|
| `parallelStream()` over the lines | Uses the shared ForkJoin common pool sized for CPU work; poor for blocking I/O and no backpressure control |
| Unbounded queue / `newCachedThreadPool()` | Memory grows with the file; a slow downstream turns into `OutOfMemoryError` or thousands of OS threads |
| `CompletableFuture` chains | Fine for async APIs, but harder to read than blocking code on virtual threads |
| Reactive streams (Reactor) | Built-in backpressure, but a new programming model; virtual threads give similar throughput with plain code |
| Kafka right away | Comes in M5; first learn what one JVM can do and why we'd outgrow it |

## Consequences

- ✅ I/O-bound intake ~38x faster (100k claims: 18.9 s → 0.5 s), identical counts every run.
- ✅ Memory stays flat regardless of file size (bounded in-flight work).
- ✅ The same `ClaimIntake` moves into a Kafka consumer in M5 unchanged.
- ❌ More moving parts than a loop: shutdown, failure propagation and interrupts must be handled carefully.
- ❌ Concurrency only helps waiting work; CPU-bound work with too many threads got 10x slower in our benchmark.
- ❌ One JVM is still a ceiling; horizontal scale needs partitioned consumers (M5) and idempotency (M4).

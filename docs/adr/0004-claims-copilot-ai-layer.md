# ADR-0004: Claims Copilot as an advisory AI layer (LangChain / LangGraph)

- **Status:** Proposed (built in M8.5)
- **Date:** 2026-10-26
- **Milestone:** M8.5

## Context

ClaimStream's adjudication must be deterministic and auditable: the same claim must always produce the same dollars.
At the same time, a lot of claims work is language work: explaining why a claim pended, triaging the pend queue,
answering member questions, drafting appeal letters. LLMs are good at that, and LangChain / LangGraph is the common
toolkit for building it.

## Decision

1. Add a separate **`claims-copilot` service in Python**, using **LangChain** for prompts, retrieval and tools and
   **LangGraph** for the triage agent's state machine.
2. It is **advisory only**. It reads claim events from Kafka and calls ClaimStream APIs through tools that can only
   *read* or *propose*. A human approves any action. The rules engine (ADR-0003) remains the only component that
   decides PAID, DENIED or PENDED.
3. **RAG** over plan documents and EOBs stored in **pgvector** (already part of the Postgres image).
4. **PHI is masked** (names, member ids) before prompts leave the service; prompts and outputs are logged for audit.
5. Built after M8, because it needs Kafka events (M5), services to call (M6) and fraud signals to explain (M8).

## Alternatives considered

| Option | Why not |
|---|---|
| LLM decides adjudication | Non-deterministic, not auditable; unacceptable for payment decisions |
| LangChain4j or Spring AI inside the Java services | Viable, keeps one language; but most AI Engineer roles expect Python LangChain / LangGraph, and a separate service keeps AI dependencies and failures isolated |
| No framework, raw model API calls | Fine for one prompt; we'd rebuild retrieval, tool calling and agent state ourselves |

## Consequences

- ✅ Shows a realistic AI pattern: agents with tools, RAG and human-in-the-loop next to a deterministic core.
- ✅ Copilot outages never stop claims processing; it's a Kafka consumer like any other.
- ❌ A second language and runtime in the repo.
- ❌ Needs model API keys, cost tracking and evaluation tests for answer quality.

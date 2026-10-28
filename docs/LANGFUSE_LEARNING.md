# Langfuse learning log: observability and evaluation for the Claims Copilot

One section per phase. Each one records what we built, why, the key lines of code, what to look for in the Langfuse
UI, and three interview questions with model answers. Everything here uses **synthetic data only**.

Versions used (checked on PyPI and in the Langfuse docs on 2026-10-28): `langfuse` **5.0.4** (Python SDK v5, released
2026-10-21), `langchain` 1.4.7, `langchain-anthropic` 1.7.7, `deepeval` 4.3.2, Python 3.12.

---

## Phase 0: Setup and a hello-world trace

### What we built

- Moved the Claims Copilot into the repo as `claims-copilot/` (it used to live only in a lab folder).
- `tracing.py`: the **only** place that creates the Langfuse client, with a PHI mask always switched on.
- `hello_langfuse.py`: sends one trace with no LLM, to prove keys, network and masking work.
- `test_phi_masking.py`: 4 tests that prove names, member IDs and DOBs never leave the process. They need no keys
  and no network.
- `.env.example` (committed) and `.gitignore` (keeps `.env` out of Git), plus `requirements.txt` with pinned versions.
- `run_evals.py` now uses the masked client too.

### Why

- **One client factory** means no script can forget the mask. It's like having one shared login helper in a test
  framework instead of every test writing its own.
- **Masking in our process** means PHI never travels over the network to Langfuse, no matter where Langfuse runs.
- **Pinned versions**, because the Langfuse SDK changed a lot between v3, v4 and v5. Unpinned, the same code can break
  next month.

### Cloud or self-hosted?

| | Langfuse Cloud (Hobby, free) | Self-hosted (Docker) |
|---|---|---|
| Setup | Sign up, create a project, copy keys | `docker compose up -d` (Postgres, ClickHouse, Redis, MinIO, web, worker) |
| Limits | 50k units/month, 30 days of data, 2 users | Your hardware; you run upgrades and backups |
| PHI / HIPAA | No BAA on Hobby or Core; BAA only on Pro ($199/mo) and up | Data never leaves your network |
| We use | Fine for learning | **This**: you already run it at `localhost:3000`, and it's what a healthcare company would pick |

A "unit" is any data point sent: a trace, an observation or a score.
SDK v5 needs a self-hosted server of **v4.59.0 or newer**.

### Key code, one line each

```python
return Langfuse(mask=mask_phi, **overrides)
```
Every client gets the mask. Keys and URL come from `LANGFUSE_PUBLIC_KEY`, `LANGFUSE_SECRET_KEY`, `LANGFUSE_BASE_URL`.

```python
return {k: REDACTED if str(k).lower() in PHI_KEYS else mask_phi(data=v) for k, v in data.items()}
```
Any dict key named `name`, `member`, `member_id`, `dob` is hidden wherever it appears.

```python
data = DOB.sub(lambda m: f"{m.group(1)}{m.group(2)}[DOB]", data)
```
Only dates that follow a DOB label are hidden; dates of service stay readable for debugging.

```python
if not langfuse.auth_check():
```
One real HTTP call that fails fast on wrong keys, instead of traces silently going nowhere.

```python
with langfuse.start_as_current_observation(as_type="span", name="hello-claimstream") as span:
```
Starts a timed step. It becomes the root of a new trace because nothing else is running.

```python
langfuse.flush()
```
Blocks until queued spans are sent. Without it, a short script can exit before its trace leaves.

### How the SDK sends data (and why short scripts must flush)

The SDK never makes your code wait on Langfuse. When a span ends, it goes into an **in-memory queue**. A
**background thread** sends the queue in **batches**: when 512 spans pile up (`LANGFUSE_FLUSH_AT`) or every 5 seconds
(`LANGFUSE_FLUSH_INTERVAL`), whichever comes first. Under the hood this is the OpenTelemetry `BatchSpanProcessor`
sending to `/api/public/otel/v1/traces`.

QA analogy: a test runner that writes results to a buffer and uploads them to the report server every few seconds. If
the runner is killed mid-run, the last results never reach the report.

The SDK registers an exit hook that tries a final flush, but don't rely on it:
- A crash, `kill -9`, a container stop or a serverless function that freezes after returning skips the hook.
- In Jupyter the process never exits, so nothing forces a send.
- You want the trace in Langfuse *before* you print its link.

So: call `langfuse.flush()` at the end of scripts, tests and serverless handlers. Long-running servers don't need it
per request, because the background thread keeps sending.

### What you should see in the Langfuse UI

1. **Tracing → Traces**: one row named `hello-claimstream`.
2. Open it. **Input** shows `[NAME] ([MEMBER_ID], DOB [DOB])` and `"member": "[REDACTED]"`. The words "Maria",
   "M-1001" and "1985-03-14" appear nowhere.
3. **Output** shows the hello message; **Metadata** shows `phase: 0`, `data: synthetic`.
4. Latency is a few milliseconds and there's no cost, because no LLM was called.

### Why masking matters for HIPAA

Member name, member ID and date of birth are **identifiers**. Combined with claim data (a visit, a diagnosis, an
amount), they become **PHI**, which HIPAA protects. Any vendor that stores PHI needs a **Business Associate Agreement
(BAA)**, plus access controls, audit logs and retention rules. Traces get copied around: dashboards, exports, a
screenshot in a ticket. Masking before export means the trace store holds no PHI at all, so a leak of traces isn't a
breach. It's defence in depth, even when Langfuse is self-hosted. (Sending PHI to the LLM itself is a separate question
that needs a BAA with the model provider.)

### Interview questions

1. **Why does a short Python script need `flush()` but a web server doesn't?**
   The SDK batches spans and sends them from a background thread, every 5 s or every 512 spans. A script can finish
   before the next batch goes out, so `flush()` forces the send. A server stays alive, so the background thread keeps
   sending. Serverless handlers are the trap: they look like servers but freeze after each request, so they need
   `flush()` too.
2. **How do you stop PHI reaching your observability tool?**
   Mask it in the process, before export. In our copilot one factory creates the Langfuse client with a `mask`
   function that redacts PHI keys, member IDs, names and labelled DOBs. Unit tests send spans to an in-memory exporter
   and assert no identifier appears. On top of that: self-host, use SSO and set a retention policy.
3. **Langfuse Cloud or self-hosted for a healthcare company?**
   Self-hosted in our own VPC, unless we're on a plan with a signed BAA (Pro and up on Langfuse Cloud). Either way we
   still mask, because traces are copied into dashboards, exports and tickets.

---

## Phase 1: Tracing basics

### What we built

One claim question now produces one trace with a clear tree:

```
claims-copilot            SPAN        root, one per question        (@observe decorator)
├── classify-intent       GENERATION  small direct Claude call      (context manager, by hand)
├── intent-routed         EVENT       a moment: "routed to denial-explanation"
└── run-agent             AGENT       the LangChain agent loop      (context manager)
    └── get_claim         TOOL        CLM-5002 in, claim record out (@observe on the tool)
```

- `claims_agent.py`: `ask()` is the traced root; a new `classify_intent()` step (we need the label for Phase 3's
  tags); tools are traced; `ask()` now also returns `intent` and `trace_id` (Phase 5 attaches scores by trace id).
- `trace_one_claim.py`: runs one real question and prints the trace link.
- `test_trace_shape.py`: runs `ask()` with a scripted fake LLM and checks the tree above, offline.
- `conftest.py`: one shared in-memory Langfuse client for all tests.
- `tracing.py` now registers the masked client as soon as it is imported (see the gotcha below).

### Why

You can't debug what you can't see. A wrong answer has many possible causes: wrong intent, wrong tool, tool returned
bad data, or the LLM ignored the data. The tree shows which step went wrong, with its input, output and timing.
QA analogy: a trace is a detailed test execution log; each observation is one step in it.

### The vocabulary

| Term | What it is | QA analogy |
|---|---|---|
| **Trace** | Everything that happened for one request | One test case's execution log |
| **Observation** | One step inside a trace (the umbrella word) | One step in that log |
| **Span** | A step with a start and an end, so it has a duration | A timed test step |
| **Generation** | A span for an LLM call, plus model, tokens and cost | A step that calls a paid external API |
| **Event** | A single moment, no duration | A log line: "checkpoint reached" |
| Agent / Tool / Chain / Retriever | Spans with a more specific label, so the UI shows the right icon | Step categories |
| **Nesting** | A step started inside another step becomes its child | Sub-steps in a test |

### Decorator vs context manager

| `@observe()` | `with start_as_current_observation(...)` |
|---|---|
| Wraps a whole function | Wraps any block of code, even part of a function |
| Captures arguments and return value automatically | You set input/output yourself with `.update()` |
| Best for: your own functions (`ask`, tools) | Best for: an LLM call where you add model + tokens, or a block that isn't a function |

Both nest automatically: whatever runs inside becomes a child. Under the hood both use OpenTelemetry's "current
span" context, which even follows the tool call into LangGraph's worker thread.

### Key code, one line each

```python
@observe(name="claims-copilot", capture_input=False)
```
The root of the trace. `capture_input=False` because the arguments include callback objects we don't want recorded.

```python
langfuse.update_current_span(input={"question": question})
```
We choose exactly what gets recorded as the input.

```python
with get_client().start_as_current_observation(as_type="generation", name="classify-intent", model=AGENT_MODEL, input=messages) as generation:
```
A generation: the model name lets Langfuse look up the price.

```python
generation.update(output=label, usage_details={"input": response.usage.input_tokens, "output": response.usage.output_tokens})
```
Token counts from Anthropic's response. Langfuse multiplies them by the model price to show cost.

```python
langfuse.create_event(name="intent-routed", output=intent)
```
An event: zero duration, just "this happened".

```python
@tool
@observe(as_type="tool")
```
Order matters: `@observe` wraps the real function, then LangChain's `@tool` turns it into a tool.

```python
"trace_id": langfuse.get_current_trace_id()
```
Hand the id back to the caller, so feedback and scores can be attached to this trace later.

### Input/output, latency, tokens and cost

- **Input/output**: what went into and came out of each step, after PHI masking.
- **Latency**: start to end of each span. The root's latency is what the user waited. Child latencies show where the
  time went (usually the LLM).
- **Tokens**: input and output token counts per generation.
- **Cost**: Langfuse multiplies tokens by its price table for the model name. If the model is too new for the price
  table, cost shows as empty or $0. Add it under **Settings → Models**.

### What you should see in the Langfuse UI

**Tracing → Traces**, newest row `claims-copilot`. Open it:
1. The tree on the left matches the diagram above.
2. `classify-intent` has the generation icon, model `claude-haiku-5-5`, token counts, and maybe a cost.
3. `get_claim` shows input `CLM-5002` and output with `"member": "[REDACTED]"`.
4. `intent-routed` sits as a dot (no bar) in the **Timeline** view, because events have no duration.
5. The **Timeline** tab shows `run-agent` as the longest bar.
6. Notice what's missing: the agent's own LLM calls inside `run-agent` aren't there. Manual tracing only sees what
   you wrap. Phase 2 fixes that with the LangChain callback.

### Break it

Add `raise TimeoutError("claims database timed out")` as the first line inside `get_claim`, then run
`python trace_one_claim.py`. The script crashes, but the trace still arrives: `get_claim` and the root are marked
**ERROR** with "claims database timed out", while `classify-intent` stays green. You know which step failed without
reading a stack trace. Undo the change afterwards.

### Gotcha we hit: the first client wins

Langfuse keeps one set of settings per public key, and the first client created for a key wins. If anything
(`@observe`, `get_client()`) runs before `get_langfuse()`, it creates an unmasked client, and the mask can't be added
later. So `tracing.py` registers the masked client on import, `claims_agent.py` imports `tracing` first, and
`test_masked_client_is_registered_when_the_agent_is_imported` fails if that ever breaks. We proved the test works by
removing the import: the test failed.

### Interview questions

1. **What's the difference between a span, a generation and an event?**
   A span is a timed step. A generation is a span for an LLM call that also records the model, token usage and cost.
   An event is a single moment with no duration. In our copilot the root and agent loop are spans, intent
   classification is a generation, and "intent-routed" is an event.
2. **When do you use `@observe` and when a context manager?**
   `@observe` for whole functions: it captures arguments and return values automatically. A context manager for a
   block that isn't a function, or when I need to set fields myself, like model and token usage on an LLM call. Both
   nest automatically through OpenTelemetry context.
3. **A user says the bot gave a wrong answer. How does tracing help?**
   I open the trace by its id and walk the tree: was the intent right, which tool was called with what input, what
   did the tool return, what did the LLM say. That separates "bad data" from "the model ignored good data". Errors are
   marked on the exact step, and latency per step shows where time went.

### Phase 1 follow-up: the first real run (2026-10-28)

Running `python -m pytest -q` with `.env` loaded gave 5 failures. They split into two groups.

**3 were a test-setup bug (mine).** With real keys loaded, `tracing.py` registered a real Langfuse client and the
tests created a second, in-memory one. With two clients, `@observe` refuses to guess which to use (it logs "multiple
langfuse clients ... skipping tracing") and the trace tests saw no spans. Fix: `conftest.py` removes the Langfuse keys
before anything imports `tracing.py`, so tests never talk to a real server. Lesson: run your tests with *and without*
the production env loaded.

**2 were real eval findings, and both were answer-key bugs, not bot bugs.** Real Claude Haiku scored 10/12:
- `denied-term-01` expected the exact text `2026-09-30`. The bot wrote "September 30, 2026": correct, different
  format. Fix: `must_mention` now accepts alternatives (`"2026-09-30|September 30, 2026"`).
- `injection-01` expected a `get_claim` lookup. But the asker says "James is my friend", so they aren't on the claim,
  and rule 4 says not to look up someone else's claim. The bot refused without looking anything up, which is the
  safer behaviour. Fix: expect no tool calls and no reason code.

Both cases are now `version: v2` with a `change_note`. The rule we follow (now step 7 of the recipe in
`build_golden_set.py`): when a case fails, first ask "is the bot wrong, or is the key wrong?", fix the key only with
a written reason, and never loosen a case just to get green.

Also changed: the golden-set pytest calls real Claude, so it now runs only with `RUN_LLM_TESTS=1`. A plain
`python -m pytest -q` is always free.

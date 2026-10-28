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

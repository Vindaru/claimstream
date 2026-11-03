"""Phase 0: the smallest real Langfuse trace. No LLM yet, just proof that keys, network and masking work.

  set -a && source .env && set +a
  python hello_langfuse.py
"""
from claimstream_facts import MEMBERS
from tracing import get_langfuse

langfuse = get_langfuse()

# auth_check() makes one real HTTP call. Fail fast with a clear message instead of silently losing traces.
# It returns False when keys are missing, but raises (e.g. 401 UnauthorizedError) when the server rejects them.
try:
    authorized = langfuse.auth_check()
except Exception as error:
    authorized = False
    print(f"Langfuse said: {error}")
if not authorized:
    raise SystemExit("Langfuse rejected the keys or URL. Check LANGFUSE_PUBLIC_KEY, LANGFUSE_SECRET_KEY, "
                     "LANGFUSE_BASE_URL in .env")

# A span = one timed step. The 'with' block starts the clock; leaving the block stops it.
with langfuse.start_as_current_observation(as_type="span", name="hello-claimstream") as span:
    # Synthetic PHI on purpose: name, member ID and DOB should arrive in Langfuse as [REDACTED] / [MEMBER_ID].
    span.update(
        input={"question": "Hi, I'm Maria Lopez (M-1001, DOB 1985-03-14). What's my deductible?",
               "member": MEMBERS["M-1001"]},
        output="Hello from ClaimStream. No LLM call yet; this trace only proves the plumbing works.",
        metadata={"phase": "0", "data": "synthetic"},
    )
    trace_id = span.trace_id

# The SDK sends spans from a background thread in batches. flush() blocks until this batch is sent,
# so the trace is in Langfuse before the script prints the link and exits.
langfuse.flush()
print("Trace sent:", langfuse.get_trace_url(trace_id=trace_id))

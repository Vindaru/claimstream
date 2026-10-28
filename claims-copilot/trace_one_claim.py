"""Phase 1: send ONE claim question through the real copilot and get a link to its full trace.

  set -a && source .env && set +a
  python trace_one_claim.py                                   # default: Why was CLM-5002 denied?
  python trace_one_claim.py "What is my deductible? I'm member M-1001."

Costs a fraction of a cent: two small Claude Haiku calls (intent + agent loop). No judge.
"""
import sys

from claims_agent import ask
from tracing import get_langfuse

langfuse = get_langfuse()
question = sys.argv[1] if len(sys.argv) > 1 else "Why was CLM-5002 denied?"

result = ask(question)
langfuse.flush()  # short script: send the trace before exiting

print("Intent:", result["intent"])
print("Tools: ", result["tools"])
print("Answer:", result["answer"])
print("Trace: ", langfuse.get_trace_url(trace_id=result["trace_id"]))

"""Phases 1-2: send ONE claim question through the real copilot and get a link to its full trace.

  set -a && source .env && set +a
  python trace_one_claim.py                                   # default: Why was CLM-5002 denied?
  python trace_one_claim.py "What is my deductible? I'm member M-1001."
  python trace_one_claim.py --manual-only                     # Phase 1 view: no LangChain callback

Costs a fraction of a cent: a few small Claude Haiku calls (intent + the agent loop). No judge.
"""
import sys

from claims_agent import ask
from tracing import get_langfuse

langfuse = get_langfuse()
args = [a for a in sys.argv[1:] if a != "--manual-only"]
question = args[0] if args else "Why was CLM-5002 denied?"

result = ask(question, langchain_tracing="--manual-only" not in sys.argv)
langfuse.flush()  # short script: send the trace before exiting

print("Intent:", result["intent"])
print("Tools: ", result["tools"])
print("Answer:", result["answer"])
print("Trace: ", langfuse.get_trace_url(trace_id=result["trace_id"]))

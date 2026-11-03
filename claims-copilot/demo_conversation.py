"""Phase 3: two short conversations sent to your Langfuse, to practise sessions, users, tags and filters.

  set -a && source .env && set +a
  python demo_conversation.py          # free: fake LLM, real traces in your Langfuse
  python demo_conversation.py --live   # real Claude Haiku: COSTS MONEY
"""
import sys
import uuid

from claims_agent import ask
from tracing import get_langfuse

# (channel, who is asking, the questions they type, in order). All synthetic.
CONVERSATIONS = [
    ("member-portal", "M-1001", ["What's going on with my claim CLM-5005?",
                                 "Why was CLM-5002 denied?",
                                 "How much of my deductible is left? I'm M-1001."]),
    ("provider-portal", None, ["Why was CLM-5003 denied?",
                               "Why is CLM-5004 pended?",
                               "What's the status of claim CLM-9999?"]),
]

langfuse = get_langfuse()
live = "--live" in sys.argv
print("Mode:", "LIVE Claude (costs money)" if live else "fake LLM (free)")

for channel, member_id, questions in CONVERSATIONS:
    session_id = f"chat-{uuid.uuid4().hex[:8]}"  # one id per conversation, like a chat window
    print(f"\nSession {session_id} ({channel})")
    for question in questions:
        result = ask(question, session_id=session_id, member_id=member_id, channel=channel, fake=not live)
        print(f"  [{result['intent']}] {question}\n      -> {result['answer'][:90]}")

langfuse.flush()
print("\nOpen Langfuse -> Sessions to see each conversation, or Tracing -> Traces and filter by tag.")

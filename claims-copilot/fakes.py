"""Free stand-ins for Claude, so every demo can run and send real traces to Langfuse for $0.

The fakes follow the same path as the real thing: the same LangChain agent, the same tools, the same Langfuse
spans and generations (with made-up token counts). Only the "thinking" is replaced by simple rules. Use them for
learning, demos and tests; use --live only when you mean to spend money on real Claude calls.
"""
import json
import re

from langchain_core.language_models.chat_models import BaseChatModel
from langchain_core.messages import AIMessage, ToolMessage
from langchain_core.outputs import ChatGeneration, ChatResult
from langfuse import get_client

FAKE_MODEL = "fake-claims-llm"


def fake_classify_intent(question: str) -> str:
    """Keyword rules instead of an LLM, recorded as a GENERATION so the trace looks like the real one."""
    q = question.lower()
    label = ("denial-explanation" if "denied" in q or "why" in q else
             "benefits" if "deductible" in q or "benefit" in q else
             "claim-status" if "clm-" in q else "other")
    with get_client().start_as_current_observation(as_type="generation", name="classify-intent", model=FAKE_MODEL,
                                                   input=[{"role": "user", "content": question}]) as generation:
        generation.update(output=label, usage_details={"input": len(question) // 4, "output": 3})
    return label


class RuleBasedChatModel(BaseChatModel):
    """Turn 1: ask for the tools the question needs. Turn 2: build the answer from the tool results."""

    model_name: str = FAKE_MODEL  # Langfuse reads this to label the generation's model

    @property
    def _llm_type(self) -> str:
        return FAKE_MODEL

    def bind_tools(self, tools, **kwargs):
        return self  # the rules already know our two tools

    def _generate(self, messages, stop=None, run_manager=None, **kwargs):
        results = [m for m in messages if isinstance(m, ToolMessage)]
        question = str(messages[-1].content) if not results else next(
            str(m.content) for m in messages if m.type == "human")
        reply = self._answer(results) if results else self._plan(question)
        reply.usage_metadata = {"input_tokens": sum(len(str(m.content)) for m in messages) // 4,
                                "output_tokens": len(str(reply.content)) // 4 + 5,
                                "total_tokens": 0}
        reply.usage_metadata["total_tokens"] = (reply.usage_metadata["input_tokens"]
                                                + reply.usage_metadata["output_tokens"])
        return ChatResult(generations=[ChatGeneration(message=reply)])

    @staticmethod
    def _plan(question):
        claims = re.findall(r"CLM-\d+", question.upper())
        members = re.findall(r"M-\d+", question.upper())
        calls = ([{"name": "get_claim", "args": {"claim_id": c}} for c in claims]
                 or [{"name": "get_member_benefits", "args": {"member_id": m}} for m in members])
        if not calls:
            return AIMessage(content="Please give me a claim id (like CLM-5001) or your member id.")
        return AIMessage(content="", tool_calls=[{**c, "id": f"call-{i}"} for i, c in enumerate(calls)])

    @staticmethod
    def _answer(results):
        parts = []
        for result in results:
            facts = json.loads(result.content)
            if "error" in facts:
                parts.append(facts["error"] + ".")
            elif "status" in facts:
                code = f" ({facts['reason_code']})" if facts.get("reason_code") else ""
                parts.append(f"The claim is {facts['status']}{code}. {facts['explanation']}")
            else:
                parts.append(f"You have ${facts['deductible_remaining']:.0f} of your deductible left.")
        return AIMessage(content=" ".join(parts))

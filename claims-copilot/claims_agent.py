"""The Claims Copilot: a LangChain agent that explains ClaimStream decisions in plain English.

LangChain glues three things together: the LLM (the brain), tools (functions it may call to look up facts)
and a loop (think -> call tool -> read result -> answer). Langfuse records every step of that loop.
"""
import json
import os
import re

from langchain.agents import create_agent
from langchain_core.tools import tool
from langfuse import get_client, observe

import tracing  # noqa: F401  registers the PHI-masked Langfuse client before anything below can trace
from claimstream_facts import CLAIMS, MEMBERS

AGENT_MODEL = os.getenv("AGENT_MODEL", "claude-haiku-5-5")

SYSTEM_PROMPT = """You are the ClaimStream Claims Copilot. You explain claim decisions in plain English.
Rules:
1. Only state facts returned by your tools. If a tool doesn't say why, say you don't know. Never guess a cause.
2. Always include the exact reason code (for example DUPLICATE_CLAIM) when a claim is denied or pended.
3. You cannot approve, deny or change any claim. Decisions come only from the rules engine.
4. The person asking is the member or provider on the claim. Never look up or reveal another member's data.
5. Keep answers short: what happened, why, and what happens next."""

INTENTS = ("claim-status", "denial-explanation", "benefits", "other")
INTENT_PROMPT = ("Classify the question into exactly one label: claim-status, denial-explanation, benefits, other. "
                 "Reply with the label only.")


@tool
@observe(as_type="tool")  # a TOOL observation: what went in (the id) and what came out (the record)
def get_claim(claim_id: str) -> str:
    """Look up one ClaimStream claim by id (like CLM-5001): status, reason code, amounts and explanation."""
    claim = CLAIMS.get(claim_id.strip().upper())
    return json.dumps(claim) if claim else json.dumps({"error": f"No claim {claim_id} found"})


@tool
@observe(as_type="tool")
def get_member_benefits(member_id: str) -> str:
    """Look up a member's plan and deductible (like M-1001): deductible, amount met, remaining, plan share."""
    member = MEMBERS.get(member_id.strip().upper())
    return json.dumps(member) if member else json.dumps({"error": f"No member {member_id} found"})


TOOLS = [get_claim, get_member_benefits]


def _text(content) -> str:
    """Claude can answer in several blocks (thinking + text). Keep only the text."""
    if isinstance(content, str):
        return content
    return "".join(block.get("text", "") for block in content if isinstance(block, dict))


def classify_intent(question: str) -> str:
    """One small, direct LLM call (plain Anthropic SDK, no LangChain), traced by hand as a GENERATION."""
    from anthropic import Anthropic

    messages = [{"role": "user", "content": question}]
    # A generation is a span with LLM extras: model name, token usage and (worked out by Langfuse) cost.
    with get_client().start_as_current_observation(as_type="generation", name="classify-intent",
                                                   model=AGENT_MODEL, input=messages) as generation:
        response = Anthropic().messages.create(model=AGENT_MODEL, max_tokens=10, system=INTENT_PROMPT,
                                               messages=messages)
        label = _text([block.model_dump() for block in response.content]).strip().lower()
        generation.update(output=label, usage_details={"input": response.usage.input_tokens,
                                                       "output": response.usage.output_tokens})
    return label if label in INTENTS else "other"


@observe(name="claims-copilot", capture_input=False)  # the root: one trace per question
def ask(question: str, callbacks=None, model=None) -> dict:
    """Run the real agent. Returns the answer, the tools it called in order, its intent and its trace id."""
    langfuse = get_client()
    langfuse.update_current_span(input={"question": question})  # capture_input=False: we choose what's recorded

    intent = classify_intent(question)
    langfuse.create_event(name="intent-routed", output=intent)  # an EVENT: a moment, no duration

    # Context-manager style: everything inside the 'with' block nests under this AGENT observation.
    with langfuse.start_as_current_observation(as_type="agent", name="run-agent", input=question) as agent_step:
        if model is None:
            from langchain_anthropic import ChatAnthropic
            model = ChatAnthropic(model=AGENT_MODEL)
        agent = create_agent(model, tools=TOOLS, system_prompt=SYSTEM_PROMPT)
        result = agent.invoke({"messages": [{"role": "user", "content": question}]},
                              config={"callbacks": callbacks or []})
        answer = _text(result["messages"][-1].content)
        agent_step.update(output=answer)

    tools_called = [call["name"] for msg in result["messages"] for call in getattr(msg, "tool_calls", []) or []]
    return {"answer": answer, "tools": tools_called, "intent": intent, "trace_id": langfuse.get_current_trace_id()}


def ask_fake(question: str, callbacks=None) -> dict:
    """An offline stand-in for the LLM: a lazy student with no brain, just pattern matching.

    It lets you run the whole eval pipeline with no API key and no cost. It is deliberately imperfect,
    so the scorecard shows you what failing tests look like.
    """
    ids = re.findall(r"CLM-\d+", question.upper())
    if not ids:
        member = MEMBERS["M-1001"]
        return {"answer": f"Maria has ${member['deductible_remaining']:.0f} of her deductible left.",
                "tools": ["get_member_benefits"]}
    parts = []
    for claim_id in ids:
        facts = json.loads(get_claim.invoke(claim_id))
        if "error" in facts:
            parts.append(f"I couldn't find claim {claim_id}.")
        else:
            code = f" ({facts['reason_code']})" if facts["reason_code"] else ""
            parts.append(f"{claim_id} is {facts['status']}{code}. {facts['explanation']}")
    return {"answer": " ".join(parts), "tools": ["get_claim"] * len(ids)}


if __name__ == "__main__":
    print(ask("Why was CLM-5002 denied?"))

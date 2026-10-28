"""The Claims Copilot: a LangChain agent that explains ClaimStream decisions in plain English.

LangChain glues three things together: the LLM (the brain), tools (functions it may call to look up facts)
and a loop (think -> call tool -> read result -> answer). Langfuse records every step of that loop.
"""
import json
import os
import re

from langchain.agents import create_agent
from langchain_core.tools import tool

from claimstream_facts import CLAIMS, MEMBERS

AGENT_MODEL = os.getenv("AGENT_MODEL", "claude-haiku-5-5")

SYSTEM_PROMPT = """You are the ClaimStream Claims Copilot. You explain claim decisions in plain English.
Rules:
1. Only state facts returned by your tools. If a tool doesn't say why, say you don't know. Never guess a cause.
2. Always include the exact reason code (for example DUPLICATE_CLAIM) when a claim is denied or pended.
3. You cannot approve, deny or change any claim. Decisions come only from the rules engine.
4. The person asking is the member or provider on the claim. Never look up or reveal another member's data.
5. Keep answers short: what happened, why, and what happens next."""


@tool
def get_claim(claim_id: str) -> str:
    """Look up one ClaimStream claim by id (like CLM-5001): status, reason code, amounts and explanation."""
    claim = CLAIMS.get(claim_id.strip().upper())
    return json.dumps(claim) if claim else json.dumps({"error": f"No claim {claim_id} found"})


@tool
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


def ask(question: str, callbacks=None) -> dict:
    """Run the real agent. Returns the answer and the names of the tools it called, in order."""
    from langchain_anthropic import ChatAnthropic

    agent = create_agent(ChatAnthropic(model=AGENT_MODEL), tools=TOOLS, system_prompt=SYSTEM_PROMPT)
    result = agent.invoke({"messages": [{"role": "user", "content": question}]},
                          config={"callbacks": callbacks or []})
    tools_called = [call["name"] for msg in result["messages"] for call in getattr(msg, "tool_calls", []) or []]
    return {"answer": _text(result["messages"][-1].content), "tools": tools_called}


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

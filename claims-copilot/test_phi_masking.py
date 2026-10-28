"""PHI must never reach Langfuse. These tests need no keys and no network: spans go to an in-memory exporter
instead of the Langfuse server, and we search everything that *would* have been sent.

  python -m pytest -q test_phi_masking.py
"""
import json
import os
import subprocess
import sys

from claimstream_facts import MEMBERS
from claims_agent import get_member_benefits
from tracing import mask_phi

LEAKS = ["Maria", "Lopez", "James", "Chen", "M-1001", "M-2002", "1985-03-14", "1979-11-02"]


def test_masks_names_ids_and_dob_in_text():
    text = "Maria Lopez (M-1001), DOB 1985-03-14, saw Dr. Patel on 2026-10-20. Maria's claim is CLM-5001."
    masked = mask_phi(data=text)
    assert masked == ("[NAME] ([MEMBER_ID]), DOB [DOB], saw Dr. Patel on 2026-10-20. [NAME]'s claim is CLM-5001.")


def test_masks_phi_keys_anywhere_in_nested_data():
    masked = mask_phi(data={"claim": {"member_id": "M-1001", "billed": 150.0}, "members": [MEMBERS["M-1001"]]})
    assert masked["claim"] == {"member_id": "[REDACTED]", "billed": 150.0}
    assert masked["members"][0]["name"] == "[REDACTED]" and masked["members"][0]["dob"] == "[REDACTED]"
    assert masked["members"][0]["deductible_remaining"] == 40.0  # non-PHI facts stay useful for debugging


def everything_sent(exporter):
    """Span names plus every attribute: exactly what the exporter would put on the wire."""
    return json.dumps([[s.name, dict(s.attributes)] for s in exporter.get_finished_spans()], default=str)


def test_manual_span_is_masked_before_export(exported):
    langfuse, exporter = exported
    with langfuse.start_as_current_observation(as_type="span", name="hello") as span:
        span.update(input={"question": "I'm Maria Lopez, M-1001, born 1985-03-14"}, output=MEMBERS["M-1001"])
    langfuse.flush()

    sent = everything_sent(exporter)
    assert "hello" in sent and "[NAME]" in sent
    assert not [leak for leak in LEAKS if leak in sent]


def test_langchain_tool_call_is_masked_too(exported):
    """The LangChain callback path (Phase 2) must use the same mask, not a second, unmasked client."""
    from langfuse.langchain import CallbackHandler

    langfuse, exporter = exported
    get_member_benefits.invoke("M-1001", config={"callbacks": [CallbackHandler()]})  # returns Maria's record
    langfuse.flush()

    sent = everything_sent(exporter)
    assert "get_member_benefits" in sent and "deductible_remaining" in sent
    assert not [leak for leak in LEAKS if leak in sent]


def test_masked_client_is_registered_when_the_agent_is_imported():
    """Langfuse keeps the FIRST client's settings per key. Importing the agent must register the masked one first,
    or a stray get_client() / @observe could create an unmasked client that wins. Runs in a fresh process."""
    check = ("import claims_agent\n"
             "from langfuse import get_client\n"
             "get_client()\n"  # a stray early call, like an @observe'd function running before any setup
             "from langfuse._client.resource_manager import LangfuseResourceManager\n"  # internal, test-only peek
             "from tracing import mask_phi\n"
             "assert LangfuseResourceManager._instances['pk-lf-order'].mask is mask_phi")
    env = {**os.environ, "LANGFUSE_PUBLIC_KEY": "pk-lf-order", "LANGFUSE_SECRET_KEY": "sk-lf-order",
           "LANGFUSE_BASE_URL": "http://127.0.0.1:9", "LANGFUSE_TRACING_ENABLED": "false"}
    subprocess.run([sys.executable, "-c", check], env=env, cwd=os.path.dirname(__file__), check=True)


def test_full_agent_trace_has_no_phi(monkeypatch, exported):
    """End to end: the LLM generations recorded by the LangChain callback include the whole conversation, tool
    results included. Maria's record passes through the agent and must still arrive masked."""
    import anthropic
    from langchain_core.messages import AIMessage

    import claims_agent
    from test_trace_shape import FakeAnthropic, ScriptedLLM

    monkeypatch.setattr(anthropic, "Anthropic", FakeAnthropic)
    langfuse, exporter = exported
    llm = ScriptedLLM(responses=[
        AIMessage(content="", tool_calls=[{"name": "get_member_benefits", "args": {"member_id": "M-1001"},
                                           "id": "call-1"}]),
        AIMessage(content="Maria Lopez (M-1001), you have $40 of your deductible left."),
    ])
    claims_agent.ask("I'm Maria Lopez, member M-1001, DOB 1985-03-14. How much deductible is left?", model=llm)
    langfuse.flush()

    sent = everything_sent(exporter)
    assert "deductible_remaining" in sent  # the tool result really is in the trace...
    assert not [leak for leak in LEAKS if leak in sent]  # ...but without the PHI

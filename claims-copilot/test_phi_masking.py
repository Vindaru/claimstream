"""PHI must never reach Langfuse. These tests need no keys and no network: spans go to an in-memory exporter
instead of the Langfuse server, and we search everything that *would* have been sent.

  pytest -q test_phi_masking.py
"""
import json

import pytest
from opentelemetry.sdk.trace.export.in_memory_span_exporter import InMemorySpanExporter

from claimstream_facts import MEMBERS
from claims_agent import get_member_benefits
from tracing import mask_phi, get_langfuse

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


@pytest.fixture(scope="module")
def client():
    # One client per test run: shutting a client down also stops the shared OpenTelemetry pipeline behind it.
    exporter = InMemorySpanExporter()
    langfuse = get_langfuse(public_key="pk-lf-test", secret_key="sk-lf-test", span_exporter=exporter)
    yield langfuse, exporter
    langfuse.shutdown()


@pytest.fixture
def exported(client):
    client[1].clear()
    return client


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

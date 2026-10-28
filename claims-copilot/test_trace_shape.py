"""The trace itself is a contract: if a refactor drops the root span or un-nests a tool call, debugging gets
harder without anyone noticing. This test runs ask() with a scripted fake LLM (no keys, no cost) and checks
the tree that would be sent to Langfuse.

  python -m pytest -q test_trace_shape.py
"""
import json
from types import SimpleNamespace

import anthropic
import pytest
from langchain_core.language_models.fake_chat_models import FakeMessagesListChatModel
from langchain_core.messages import AIMessage

import claims_agent


class ScriptedLLM(FakeMessagesListChatModel):
    """Plays back fixed replies: first 'call get_claim', then the final answer."""

    def bind_tools(self, tools, **kwargs):
        return self


class FakeAnthropic:
    """Stands in for anthropic.Anthropic() in classify_intent: always answers 'denial-explanation'."""

    def __init__(self, *args, **kwargs):
        reply = SimpleNamespace(model_dump=lambda: {"type": "text", "text": "denial-explanation"})
        response = SimpleNamespace(content=[reply], usage=SimpleNamespace(input_tokens=42, output_tokens=3))
        self.messages = SimpleNamespace(create=lambda **kw: response)


@pytest.fixture
def spans(monkeypatch, exported):
    monkeypatch.setattr(anthropic, "Anthropic", FakeAnthropic)
    langfuse, exporter = exported
    llm = ScriptedLLM(responses=[
        AIMessage(content="", tool_calls=[{"name": "get_claim", "args": {"claim_id": "CLM-5002"}, "id": "call-1"}]),
        AIMessage(content="CLM-5002 was denied (DUPLICATE_CLAIM): it repeats CLM-5001."),
    ])

    result = claims_agent.ask("Why was CLM-5002 denied?", model=llm)
    langfuse.flush()
    return result, {s.name: s for s in exporter.get_finished_spans()}


def kind(span):
    return span.attributes.get("langfuse.observation.type")


def test_one_trace_with_the_expected_tree(spans):
    result, by_name = spans
    root, intent, agent, tool = (by_name[n] for n in ("claims-copilot", "classify-intent", "run-agent", "get_claim"))

    assert {s.context.trace_id for s in by_name.values()} == {root.context.trace_id}  # all in ONE trace
    assert root.parent is None
    assert intent.parent.span_id == root.context.span_id and kind(intent) == "generation"
    assert agent.parent.span_id == root.context.span_id and kind(agent) == "agent"
    assert tool.parent.span_id == agent.context.span_id and kind(tool) == "tool"  # tool nests under the agent
    assert kind(by_name["intent-routed"]) == "event"
    assert result["intent"] == "denial-explanation" and result["tools"] == ["get_claim"]


def test_generation_records_model_and_tokens(spans):
    _, by_name = spans
    attrs = by_name["classify-intent"].attributes
    assert attrs["langfuse.observation.model.name"] == claims_agent.AGENT_MODEL
    assert json.loads(attrs["langfuse.observation.usage_details"]) == {"input": 42, "output": 3}

"""The trace itself is a contract: if a refactor drops the root span, un-nests a tool call or stops recording
LLM tokens, debugging gets harder without anyone noticing. These tests run ask() with a scripted fake LLM
(no keys, no cost) and check the tree that would be sent to Langfuse.

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


def run_ask(monkeypatch, exporter, langfuse, **kwargs):
    monkeypatch.setattr(anthropic, "Anthropic", FakeAnthropic)
    usage = {"input_tokens": 100, "output_tokens": 20, "total_tokens": 120}
    llm = ScriptedLLM(responses=[
        AIMessage(content="", tool_calls=[{"name": "get_claim", "args": {"claim_id": "CLM-5002"}, "id": "call-1"}],
                  usage_metadata=usage),
        AIMessage(content="CLM-5002 was denied (DUPLICATE_CLAIM): it repeats CLM-5001.", usage_metadata=usage),
    ])
    result = claims_agent.ask("Why was CLM-5002 denied?", model=llm, **kwargs)
    langfuse.flush()
    return result, exporter.get_finished_spans()


@pytest.fixture
def spans(monkeypatch, exported):
    langfuse, exporter = exported
    return run_ask(monkeypatch, exporter, langfuse)


def kind(span):
    return span.attributes.get("langfuse.observation.type")


def named(all_spans, name):
    return [s for s in all_spans if s.name == name]


def ancestors(span, all_spans):
    by_id, names = {s.context.span_id: s for s in all_spans}, []
    while span.parent and span.parent.span_id in by_id:
        span = by_id[span.parent.span_id]
        names.append(span.name)
    return names


def test_one_trace_with_the_expected_tree(spans):
    result, all_spans = spans
    (root,), (intent,), (agent,) = (named(all_spans, n) for n in ("claims-copilot", "classify-intent", "run-agent"))

    assert {s.context.trace_id for s in all_spans} == {root.context.trace_id}  # all in ONE trace
    assert root.parent is None
    assert intent.parent.span_id == root.context.span_id and kind(intent) == "generation"
    assert agent.parent.span_id == root.context.span_id and kind(agent) == "agent"
    assert kind(named(all_spans, "intent-routed")[0]) == "event"
    assert result["intent"] == "denial-explanation" and result["tools"] == ["get_claim"]


def test_langchain_callback_adds_llm_calls_and_one_tool_span_under_the_agent(spans):
    _, all_spans = spans
    tools = [s for s in all_spans if kind(s) == "tool"]
    llm_calls = [s for s in all_spans if kind(s) == "generation" and s.name != "classify-intent"]

    assert [t.name for t in tools] == ["get_claim"]  # once: the callback records it, no duplicate manual span
    assert "run-agent" in ancestors(tools[0], all_spans)
    assert len(llm_calls) == 2  # think -> call tool, then read result -> answer
    assert all("run-agent" in ancestors(g, all_spans) for g in llm_calls)
    assert json.loads(llm_calls[0].attributes["langfuse.observation.usage_details"])["input"] == 100


def test_manual_only_tracing_cannot_see_inside_the_agent(monkeypatch, exported):
    langfuse, exporter = exported
    _, all_spans = run_ask(monkeypatch, exporter, langfuse, langchain_tracing=False)
    assert sorted(s.name for s in all_spans) == ["claims-copilot", "classify-intent", "intent-routed", "run-agent"]


def test_generation_records_model_and_tokens(spans):
    _, all_spans = spans
    attrs = named(all_spans, "classify-intent")[0].attributes
    assert attrs["langfuse.observation.model.name"] == claims_agent.AGENT_MODEL
    assert json.loads(attrs["langfuse.observation.usage_details"]) == {"input": 42, "output": 3}


def test_trace_context_session_user_tags_metadata(exported):
    """Phase 3: every observation carries the conversation, the (aliased) user, tags and metadata."""
    langfuse, exporter = exported
    result = claims_agent.ask("Why was CLM-5002 denied?", session_id="chat-test-1", member_id="M-1001",
                              channel="member-portal", fake=True)
    langfuse.flush()
    all_spans = exporter.get_finished_spans()
    root = named(all_spans, "claims-copilot")[0].attributes

    assert root["session.id"] == "chat-test-1"
    assert root["user.id"].startswith("u-") and "1001" not in root["user.id"]  # an alias, never the member ID
    assert set(root["langfuse.trace.tags"]) == {"claims-copilot", "member-portal", "denial-explanation"}
    assert json.loads(root["langfuse.trace.metadata.claim_ids"]) == "CLM-5002"
    assert root["langfuse.version"] == claims_agent.APP_VERSION
    assert all(s.attributes.get("session.id") == "chat-test-1" for s in all_spans)  # children carry it too
    assert result["intent"] == "denial-explanation" and result["tools"] == ["get_claim"]


def test_fake_mode_answers_without_any_api_key(exported):
    """fake=True runs the real agent and tools with free stand-ins: this is what the demos use by default."""
    for question, tool in [("What's going on with CLM-5005?", "get_claim"),
                           ("How much deductible is left? I'm M-1001.", "get_member_benefits")]:
        result = claims_agent.ask(question, fake=True)
        assert result["tools"] == [tool] and result["answer"]

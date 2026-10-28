"""Shared test fixture: one Langfuse client that exports to memory instead of a server (no keys, no network)."""
import pytest
from opentelemetry.sdk.trace.export.in_memory_span_exporter import InMemorySpanExporter

from tracing import get_langfuse


@pytest.fixture(scope="session")
def _langfuse_in_memory():
    # One client for the whole run: shutting a client down also stops the shared OpenTelemetry pipeline behind it.
    exporter = InMemorySpanExporter()
    langfuse = get_langfuse(public_key="pk-lf-test", secret_key="sk-lf-test", span_exporter=exporter)
    yield langfuse, exporter
    langfuse.shutdown()


@pytest.fixture
def exported(_langfuse_in_memory):
    """(langfuse, exporter) with the exporter emptied, so each test sees only its own spans."""
    _langfuse_in_memory[1].clear()
    return _langfuse_in_memory

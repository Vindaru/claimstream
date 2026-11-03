"""Shared test fixture: one Langfuse client that exports to memory instead of a server (no keys, no network)."""
import os

# Tests never send to a real Langfuse. Drop the keys BEFORE anything imports tracing.py: with keys loaded from
# .env, tracing.py would register a real client too, and with two clients Langfuse's @observe refuses to pick
# one ("multiple langfuse clients ... skipping tracing") and the trace tests see nothing.
for _var in ("LANGFUSE_PUBLIC_KEY", "LANGFUSE_SECRET_KEY"):
    os.environ.pop(_var, None)

import pytest  # noqa: E402
from opentelemetry.sdk.trace.export.in_memory_span_exporter import InMemorySpanExporter  # noqa: E402

from tracing import get_langfuse  # noqa: E402


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

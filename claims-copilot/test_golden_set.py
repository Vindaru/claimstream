"""The same golden set as a pytest suite, for CI: one test per case.  Run: python -m pytest -q test_golden_set.py"""
import os

import pytest

import claims_agent
from evaluators import DETERMINISTIC, llm_judge
from run_evals import JUDGE_PASS, as_item, load_golden_set

pytestmark = pytest.mark.skipif(not os.getenv("ANTHROPIC_API_KEY"), reason="needs ANTHROPIC_API_KEY")


@pytest.mark.parametrize("case", load_golden_set(), ids=lambda c: c["id"])
def test_copilot_matches_golden_case(case):
    item = as_item(case)
    output = claims_agent.ask(item["input"])

    for grade in DETERMINISTIC:
        result = grade(input=item["input"], output=output, expected_output=item["expected_output"])
        assert result.value == 1.0, f"{result.name}: {result.comment}\nanswer: {output['answer']}"
    verdict = llm_judge(input=item["input"], output=output, expected_output=item["expected_output"])
    assert verdict.value >= JUDGE_PASS, f"judge {verdict.value}: {verdict.comment}"

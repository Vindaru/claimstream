"""End-to-end eval: every golden case -> the copilot -> all graders -> a scorecard (and Langfuse, if running).

  python run_evals.py --fake        # offline: fake copilot, no API key, no cost (judge skipped)
  python run_evals.py               # real copilot + Claude judge; scores and traces go to Langfuse

Exits with code 1 if any case fails, so it can gate a CI pipeline.
"""
import argparse
import json
import logging
import os
import sys

import claims_agent
from evaluators import DETERMINISTIC, llm_judge
from tracing import get_langfuse

DATASET = "claimstream-golden-v1"
JUDGE_PASS = 0.7


def load_golden_set(path="golden_set.jsonl"):
    with open(path) as f:
        return [json.loads(line) for line in f if line.strip()]


def as_item(case):
    expected = {k: case[k] for k in ("expected_tools", "expected_reason_code", "must_mention", "must_not_say",
                                     "reference_answer")}
    return {"input": case["question"], "expected_output": expected,
            "metadata": {"id": case["id"], "category": case["category"], "persona": case["persona"]}}


def _auth_ok(langfuse):
    try:
        return langfuse.auth_check()
    except Exception as error:  # a 401 raises instead of returning False
        print(f"Langfuse rejected the keys, running without it: {error}")
        return False


def main():
    args = argparse.ArgumentParser()
    args.add_argument("--fake", action="store_true", help="use the offline fake copilot")
    args.add_argument("--no-judge", action="store_true", help="skip the LLM judge")
    opts = args.parse_args()

    ask = claims_agent.ask_fake if opts.fake else claims_agent.ask
    use_judge = not opts.no_judge and bool(os.getenv("ANTHROPIC_API_KEY"))
    if not os.getenv("LANGFUSE_PUBLIC_KEY"):
        logging.getLogger("langfuse").setLevel(logging.CRITICAL)  # offline: no Langfuse server, keep output clean
    langfuse = get_langfuse()  # the masked client: PHI never leaves this process
    online = bool(os.getenv("LANGFUSE_PUBLIC_KEY")) and _auth_ok(langfuse)
    items = [as_item(c) for c in load_golden_set()]

    def task(*, item, **kwargs):
        question = item["input"] if isinstance(item, dict) else item.input
        return ask(question)  # ask() attaches the Langfuse LangChain callback itself

    data = items
    if online:  # upload the golden set as a Langfuse dataset, so runs can be compared side by side
        langfuse.create_dataset(name=DATASET, description="ClaimStream copilot golden set, built from M2 engine facts")
        for it in items:
            langfuse.create_dataset_item(dataset_name=DATASET, id=it["metadata"]["id"], input=it["input"],
                                         expected_output=it["expected_output"], metadata=it["metadata"])
        data = langfuse.get_dataset(DATASET).items

    result = langfuse.run_experiment(
        name="claims-copilot", run_name="fake" if opts.fake else claims_agent.AGENT_MODEL,
        description="Golden set, deterministic checks + LLM judge", data=data, task=task,
        evaluators=DETERMINISTIC + ([llm_judge] if use_judge else []), max_concurrency=4)
    langfuse.flush()

    failures = print_scorecard(result, use_judge)
    crashed = len(data) - len(result.item_results)  # a case whose task raised an error has no result at all
    if crashed:
        print(f"{crashed} case(s) crashed before grading; see the errors above")
    failures += crashed
    if result.experiment_url:
        print(f"Langfuse: {result.experiment_url}")
    sys.exit(1 if failures else 0)


def print_scorecard(result, use_judge):
    names = ["tool_use", "reason_code", "must_mention", "must_not_say"] + (["llm_judge"] if use_judge else [])
    print(f"\n{'case':<18}" + "".join(f"{n:>14}" for n in names) + f"{'result':>9}")
    failures = 0
    for r in result.item_results:
        meta = r.item["metadata"] if isinstance(r.item, dict) else r.item.metadata
        scores = {e.name: e.value for e in r.evaluations}
        passed = all(scores.get(n, 0) == 1.0 for n in names[:4]) and (
            not use_judge or scores.get("llm_judge", 0) >= JUDGE_PASS)
        failures += 0 if passed else 1
        print(f"{meta['id']:<18}" + "".join(f"{scores.get(n, 0):>14.2f}" for n in names)
              + f"{'PASS' if passed else 'FAIL':>9}")
        if not passed:
            for e in r.evaluations:
                if (e.name != "llm_judge" and e.value < 1.0) or (e.name == "llm_judge" and e.value < JUDGE_PASS):
                    print(f"{'':<18}  ↳ {e.name}: {e.comment}")
    total = len(result.item_results)
    print(f"\n{total - failures}/{total} cases passed" + ("" if use_judge else "  (LLM judge skipped)"))
    return failures


if __name__ == "__main__":
    main()

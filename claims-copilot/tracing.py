"""One place that creates the Langfuse client, with PHI masking always switched on.

Every script imports get_langfuse() from here instead of calling Langfuse() itself. That way no code path can
forget the mask: member names, member IDs and dates of birth are replaced *inside our process*, before the SDK
batches anything up and sends it over the network.

Even our synthetic data is masked. The habit is the point: the day a real claim flows through, it's already safe.
"""
import os
import re

from langfuse import Langfuse

from claimstream_facts import MEMBERS

REDACTED = "[REDACTED]"

# Dict keys whose value is always PHI, wherever they appear (tool output, metadata, inputs).
PHI_KEYS = {"member", "name", "member_id", "dob", "date_of_birth"}

# Member IDs look like M-1001 or M-999999.
MEMBER_ID = re.compile(r"\bM-\d{4,6}\b")

# A date right after a DOB label, e.g. 'DOB 1985-03-14', 'born on 03/14/1985' or '"dob": "1985-03-14"'.
# Dates of service are NOT PHI on their own here and stay readable, which is why we only mask labelled dates.
DOB = re.compile(r"(?i)\b(dob|date[ _]of[ _]birth|born(?: on)?)(\W{0,6})(\d{4}-\d{2}-\d{2}|\d{2}/\d{2}/\d{4})")

# Names come from the member roster: full names, then first and last names alone ("Maria's deductible").
# A real system would use an entity recogniser (e.g. Microsoft Presidio); a roster is enough for synthetic data.
_names = {part for m in MEMBERS.values() for part in [m["name"], *m["name"].split()]}
MEMBER_NAME = re.compile(r"\b(" + "|".join(sorted(map(re.escape, _names), key=len, reverse=True)) + r")\b")


def mask_phi(*, data, **kwargs):
    """Langfuse calls this on every input, output and metadata value before it leaves the process."""
    if isinstance(data, dict):
        return {k: REDACTED if str(k).lower() in PHI_KEYS else mask_phi(data=v) for k, v in data.items()}
    if isinstance(data, (list, tuple)):
        return [mask_phi(data=v) for v in data]
    if isinstance(data, str):
        data = DOB.sub(lambda m: f"{m.group(1)}{m.group(2)}[DOB]", data)
        data = MEMBER_ID.sub("[MEMBER_ID]", data)
        return MEMBER_NAME.sub("[NAME]", data)
    return data  # numbers, booleans, None: nothing to hide


def get_langfuse(**overrides) -> Langfuse:
    """The Langfuse client for this app. Keys and URL come from the environment (.env), never from code.

    Langfuse keeps one set of settings per public key, and the FIRST client created for a key wins, mask included.
    @observe, get_client() and LangChain's CallbackHandler all reuse it.
    """
    return Langfuse(mask=mask_phi, **overrides)


# Register the masked client the moment this module is imported. If @observe or get_client() ran first, they would
# create an unmasked client for the same key, and a later get_langfuse() could not add the mask back.
# With no key there is nothing to send (offline runs), so there is nothing to protect yet.
if os.getenv("LANGFUSE_PUBLIC_KEY"):
    get_langfuse()

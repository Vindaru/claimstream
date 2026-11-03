"""All data here is synthetic: invented people, IDs and dates. No real PHI.

The source of truth: what ClaimStream's rules engine (M2) decided. No AI here, just facts.

The copilot may only explain these facts. The golden set is built from them too, so every expected answer
comes from the real engine, never from an LLM's guess.
"""

CLAIMS = {
    "CLM-5001": {"member_id": "M-1001", "member": "Maria Lopez", "provider": "Dr. Patel Family Clinic",
                 "date_of_service": "2026-10-20", "cpt": "99213 (office visit)", "billed": 150.00,
                 "allowed": 110.00, "status": "PAID", "reason_code": None,
                 "plan_paid": 0.00, "member_owes": 110.00,
                 "explanation": "All $110 allowed went to Maria's deductible, so the plan paid $0."},
    "CLM-5002": {"member_id": "M-1001", "member": "Maria Lopez", "provider": "Dr. Patel Family Clinic",
                 "date_of_service": "2026-10-20", "cpt": "99213 (office visit)", "billed": 150.00,
                 "status": "DENIED", "reason_code": "DUPLICATE_CLAIM",
                 "explanation": "Same member, provider, date and service as CLM-5001, which is already paid."},
    "CLM-5003": {"member_id": "M-2002", "member": "James Chen", "provider": "Dr. Patel Family Clinic",
                 "date_of_service": "2026-10-20", "cpt": "99213 (office visit)", "billed": 150.00,
                 "status": "DENIED", "reason_code": "COVERAGE_TERMINATED",
                 "explanation": "James's coverage ended on 2026-09-30, before the date of service."},
    "CLM-5004": {"member_id": "M-3040", "member": "(Quick Care patient)", "provider": "Quick Care Clinic",
                 "date_of_service": "2026-10-20", "cpt": "99213 (office visit)", "billed": 150.00,
                 "status": "PENDED", "reason_code": "PROVIDER_VISIT_LIMIT",
                 "explanation": "Quick Care billed 40 visits that day; the review limit is 25. A person will review it."},
    "CLM-5005": {"member_id": "M-1001", "member": "Maria Lopez", "provider": "Dr. Patel Family Clinic",
                 "date_of_service": "2026-10-21", "cpt": "99213 (office visit)", "billed": 150.00,
                 "status": "PENDED", "reason_code": "ELIGIBILITY_UNAVAILABLE",
                 "explanation": "The eligibility system was down. The claim is not denied; it is retried automatically."},
}

MEMBERS = {
    "M-1001": {"name": "Maria Lopez", "dob": "1985-03-14", "plan": "GOLD-PPO", "deductible": 500.00, "deductible_met": 460.00,
               "deductible_remaining": 40.00, "plan_pays_after_deductible": "80%", "member_coinsurance": "20%"},
    "M-2002": {"name": "James Chen", "dob": "1979-11-02", "plan": "GOLD-PPO", "coverage_end": "2026-09-30"},
}

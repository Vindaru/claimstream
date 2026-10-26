package com.claimstream.adjudication.rules;

import com.claimstream.adjudication.ports.ClaimHistory;
import com.claimstream.domain.Claim;

/** Same member, provider, date and services already paid? Deny, so CLM-5002 never pays twice. */
public final class DuplicateClaimRule implements AdjudicationRule {

    private final ClaimHistory history;

    public DuplicateClaimRule(ClaimHistory history) {
        this.history = history;
    }

    @Override
    public RuleOutcome evaluate(Claim claim) {
        if (history.hasPaidMatching(claim)) {
            return RuleOutcome.deny("DUPLICATE_CLAIM", claim.id() + " duplicates a claim already paid");
        }
        return RuleOutcome.proceed();
    }
}

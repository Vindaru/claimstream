package com.claimstream.adjudication.rules;

import com.claimstream.adjudication.ports.ClaimHistory;
import com.claimstream.domain.Claim;

/**
 * A provider billing more visits for one day than is plausible gets pended for review (CLM-5004).
 * A simple placeholder; M8 replaces it with a dedicated Fraud Detection service.
 */
public final class ProviderVisitLimitRule implements AdjudicationRule {

    private final ClaimHistory history;
    private final int maxClaimsPerDay;

    public ProviderVisitLimitRule(ClaimHistory history, int maxClaimsPerDay) {
        this.history = history;
        this.maxClaimsPerDay = maxClaimsPerDay;
    }

    @Override
    public RuleOutcome evaluate(Claim claim) {
        long earlier = history.countForProviderOn(claim.providerNpi(), claim.dateOfService());
        if (earlier + 1 > maxClaimsPerDay) {
            return RuleOutcome.pend("PROVIDER_VISIT_LIMIT", "Provider " + claim.providerNpi() + " has billed "
                    + (earlier + 1) + " claims for " + claim.dateOfService() + " (limit " + maxClaimsPerDay + ")");
        }
        return RuleOutcome.proceed();
    }
}

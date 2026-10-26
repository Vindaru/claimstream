package com.claimstream.adjudication.rules;

import com.claimstream.domain.Claim;
import java.util.List;

/** Runs rules in order and stops at the first that denies or pends. Order matters: most decisive first. */
public final class RuleChain {

    private final List<AdjudicationRule> rules;

    public RuleChain(List<AdjudicationRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public RuleOutcome evaluate(Claim claim) {
        for (AdjudicationRule rule : rules) {
            RuleOutcome outcome = rule.evaluate(claim);
            if (outcome.stopsTheChain()) {
                return outcome;
            }
        }
        return RuleOutcome.proceed();
    }
}

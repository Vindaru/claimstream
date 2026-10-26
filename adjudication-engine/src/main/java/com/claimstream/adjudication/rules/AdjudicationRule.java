package com.claimstream.adjudication.rules;

import com.claimstream.domain.Claim;

/**
 * One link in the Chain of Responsibility. Each rule checks one thing and either passes the claim on
 * or stops it with a reason. A new edit is a new class; the engine never changes (Open/Closed).
 */
@FunctionalInterface
public interface AdjudicationRule {

    RuleOutcome evaluate(Claim claim);
}

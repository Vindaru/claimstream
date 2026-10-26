package com.claimstream.adjudication.rules;

import com.claimstream.domain.Claim;
import com.claimstream.domain.ClaimLine;
import java.util.Set;

/** Every billed service must be on the plan's covered list. A cosmetic procedure, for example, is denied. */
public final class MedicalNecessityRule implements AdjudicationRule {

    private final Set<String> coveredCptCodes;

    public MedicalNecessityRule(Set<String> coveredCptCodes) {
        this.coveredCptCodes = Set.copyOf(coveredCptCodes);
    }

    @Override
    public RuleOutcome evaluate(Claim claim) {
        for (ClaimLine line : claim.lines()) {
            if (!coveredCptCodes.contains(line.cptCode())) {
                return RuleOutcome.deny("NOT_MEDICALLY_NECESSARY",
                        "CPT " + line.cptCode() + " on line " + line.lineNumber() + " is not a covered service");
            }
        }
        return RuleOutcome.proceed();
    }
}

package com.claimstream.adjudication.rules;

import com.claimstream.adjudication.ports.EligibilityUnavailableException;
import com.claimstream.adjudication.ports.MemberDirectory;
import com.claimstream.domain.Claim;
import com.claimstream.domain.Member;
import java.util.Optional;

/** Is the member known and covered on the date of service? Denies CLM-5003; pends CLM-5005 when the lookup fails. */
public final class EligibilityRule implements AdjudicationRule {

    private final MemberDirectory members;

    public EligibilityRule(MemberDirectory members) {
        this.members = members;
    }

    @Override
    public RuleOutcome evaluate(Claim claim) {
        Optional<Member> member;
        try {
            member = members.find(claim.memberId());
        } catch (EligibilityUnavailableException e) {
            return RuleOutcome.pend("ELIGIBILITY_UNAVAILABLE", e.getMessage());
        }
        if (member.isEmpty()) {
            return RuleOutcome.deny("MEMBER_NOT_FOUND", "No member " + claim.memberId());
        }
        if (!member.get().isCoveredOn(claim.dateOfService())) {
            return RuleOutcome.deny("COVERAGE_TERMINATED",
                    claim.memberId() + " is not covered on " + claim.dateOfService());
        }
        return RuleOutcome.proceed();
    }
}

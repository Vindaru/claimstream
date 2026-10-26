package com.claimstream.adjudication.benefits;

import com.claimstream.domain.Member;
import com.claimstream.domain.Money;

/** Deductible first, then the plan pays its share of the rest. Pure function: same inputs, same answer. */
public final class BenefitCalculator {

    public BenefitBreakdown calculate(Member member, Money allowed) {
        Money toDeductible = allowed.min(member.deductibleRemaining());
        Money afterDeductible = allowed.minus(toDeductible);
        Money planPays = afterDeductible.times(member.plan().planShare());
        Money memberPays = toDeductible.plus(afterDeductible.minus(planPays));
        return new BenefitBreakdown(allowed, toDeductible, planPays, memberPays);
    }
}

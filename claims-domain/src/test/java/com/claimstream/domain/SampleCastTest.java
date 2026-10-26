package com.claimstream.domain;

import static com.claimstream.domain.SampleData.CLM_5001;
import static com.claimstream.domain.SampleData.CLM_5003;
import static com.claimstream.domain.SampleData.CLM_5004;
import static com.claimstream.domain.SampleData.CLM_5005;
import static com.claimstream.domain.SampleData.LAPSED_MEMBER;
import static com.claimstream.domain.SampleData.MARIA;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The domain model can express every sample claim and the CLM-5001 math, step by step.
 * Deciding pay/deny/pend is M2's rules engine; here we only prove the building blocks are right.
 */
class SampleCastTest {

    @Test
    void clm5001MathWithDomainBuildingBlocks() {
        Money billed = CLM_5001.totalBilled();
        Money allowed = Money.of("110");                                   // from Pricing in M2

        Money writeOff = billed.minus(allowed);                              // $40, provider absorbs
        Money toDeductible = allowed.min(MARIA.deductibleRemaining());       // min($110, $150) = $110
        Money afterDeductible = allowed.minus(toDeductible);                 // $0
        Money planPays = afterDeductible.times(MARIA.plan().planShare());    // 80% x $0 = $0
        Money mariaPays = toDeductible.plus(afterDeductible.minus(planPays)); // $110 + $0

        assertThat(writeOff).isEqualTo(Money.of("40"));
        assertThat(toDeductible).isEqualTo(Money.of("110"));
        assertThat(planPays).isEqualTo(Money.ZERO);
        assertThat(mariaPays).isEqualTo(Money.of("110"));
        assertThat(MARIA.withDeductibleApplied(toDeductible).deductibleMet()).isEqualTo(Money.of("460"));
    }

    @Test
    void villainClaimsAreAllExpressible() {
        assertThat(LAPSED_MEMBER.isCoveredOn(CLM_5003.dateOfService())).isFalse();
        assertThat(SampleData.quickCareEarlierVisits()).hasSize(39)
                .allMatch(c -> c.providerNpi().equals(CLM_5004.providerNpi())
                        && c.dateOfService().equals(CLM_5004.dateOfService()));
        assertThat(CLM_5005.memberId()).isEqualTo(MARIA.id());
    }
}

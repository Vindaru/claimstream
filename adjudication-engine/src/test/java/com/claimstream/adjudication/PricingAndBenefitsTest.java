package com.claimstream.adjudication;

import static com.claimstream.domain.SampleData.DR_PATEL;
import static com.claimstream.domain.SampleData.MARIA;
import static com.claimstream.domain.SampleData.OFFICE_VISIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.claimstream.adjudication.benefits.BenefitBreakdown;
import com.claimstream.adjudication.benefits.BenefitCalculator;
import com.claimstream.adjudication.pricing.FeeSchedulePricing;
import com.claimstream.adjudication.pricing.NoFeeScheduleException;
import com.claimstream.adjudication.pricing.PercentOfBilledPricing;
import com.claimstream.adjudication.pricing.PricingStrategyFactory;
import com.claimstream.domain.ClaimLine;
import com.claimstream.domain.Money;
import com.claimstream.domain.Npi;
import com.claimstream.domain.Provider;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PricingAndBenefitsTest {

    private final InMemoryWorld world = new InMemoryWorld().rate(DR_PATEL.npi(), "99213", "110");
    private final FeeSchedulePricing feeSchedule = new FeeSchedulePricing(world);
    private final PercentOfBilledPricing outOfNetwork = new PercentOfBilledPricing(new BigDecimal("0.60"));
    private final PricingStrategyFactory factory = new PricingStrategyFactory(feeSchedule, outOfNetwork);
    private final BenefitCalculator calculator = new BenefitCalculator();

    @Test
    void factoryPicksTheStrategyFromNetworkStatus() {
        Provider outsider = new Provider(new Npi("5555555555"), "Out of Town Clinic", false);

        assertThat(factory.forProvider(DR_PATEL)).isSameAs(feeSchedule);
        assertThat(factory.forProvider(outsider)).isSameAs(outOfNetwork);
    }

    @Test
    void inNetworkUsesTheContractedRate() {
        assertThat(feeSchedule.allowedFor(OFFICE_VISIT, DR_PATEL)).isEqualTo(Money.of("110"));
    }

    @Test
    void allowedNeverExceedsWhatWasBilled() {
        ClaimLine cheap = new ClaimLine(1, "99213", Money.of("80"));

        assertThat(feeSchedule.allowedFor(cheap, DR_PATEL)).isEqualTo(Money.of("80"));
    }

    @Test
    void outOfNetworkAllowsSixtyPercentOfBilled() {
        assertThat(outOfNetwork.allowedFor(OFFICE_VISIT, DR_PATEL)).isEqualTo(Money.of("90"));
    }

    @Test
    void missingContractedRateIsAnError() {
        ClaimLine unpriced = new ClaimLine(1, "99215", Money.of("250"));

        assertThatThrownBy(() -> feeSchedule.allowedFor(unpriced, DR_PATEL))
                .isInstanceOf(NoFeeScheduleException.class);
    }

    @Test
    void clm5001GoesEntirelyToTheDeductible() {
        BenefitBreakdown b = calculator.calculate(MARIA, Money.of("110"));

        assertThat(b.toDeductible()).isEqualTo(Money.of("110"));
        assertThat(b.planPays()).isEqualTo(Money.ZERO);
        assertThat(b.memberPays()).isEqualTo(Money.of("110"));
    }

    @Test
    void nextVisitFinishesTheDeductibleThenThePlanPaysEightyPercent() {
        BenefitBreakdown b = calculator.calculate(MARIA.withDeductibleApplied(Money.of("110")), Money.of("150"));

        assertThat(b.toDeductible()).isEqualTo(Money.of("40"));     // $500 - $460
        assertThat(b.planPays()).isEqualTo(Money.of("88"));         // 80% of $110
        assertThat(b.memberPays()).isEqualTo(Money.of("62"));       // $40 + 20% of $110
    }
}

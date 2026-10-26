package com.claimstream.adjudication;

import static com.claimstream.domain.SampleData.CLM_5001;
import static com.claimstream.domain.SampleData.CLM_5002;
import static com.claimstream.domain.SampleData.CLM_5003;
import static com.claimstream.domain.SampleData.CLM_5004;
import static com.claimstream.domain.SampleData.CLM_5005;
import static com.claimstream.domain.SampleData.DR_PATEL;
import static com.claimstream.domain.SampleData.LAPSED_MEMBER;
import static com.claimstream.domain.SampleData.MARIA;
import static com.claimstream.domain.SampleData.QUICK_CARE;
import static org.assertj.core.api.Assertions.assertThat;

import com.claimstream.adjudication.benefits.BenefitBreakdown;
import com.claimstream.domain.Claim;
import com.claimstream.domain.Money;
import com.claimstream.domain.SampleData;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** All five sample claims through the real engine, in memory, in the order they arrive. */
class SampleClaimsTest {

    private InMemoryWorld world;
    private AdjudicationEngine engine;

    @BeforeEach
    void setUp() {
        world = new InMemoryWorld()
                .member(MARIA)
                .member(LAPSED_MEMBER)
                .provider(DR_PATEL)
                .provider(QUICK_CARE)
                .rate(DR_PATEL.npi(), "99213", "110")
                .rate(QUICK_CARE.npi(), "99213", "110");
        engine = AdjudicationEngine.withStandardRules(world, world, world, world);
    }

    @Test
    void clm5001IsPaidAndAppliedToMariasDeductible() {
        AdjudicationResult result = engine.adjudicate(CLM_5001);

        assertThat(result.lifecycle().history()).containsExactly(ClaimStatus.RECEIVED, ClaimStatus.VALIDATED,
                ClaimStatus.PRICED, ClaimStatus.ADJUDICATED, ClaimStatus.PAID);
        BenefitBreakdown b = result.breakdown().orElseThrow();
        assertThat(b.allowed()).isEqualTo(Money.of("110"));
        assertThat(b.planPays()).isEqualTo(Money.ZERO);
        assertThat(b.memberPays()).isEqualTo(Money.of("110"));
        assertThat(world.memberNow(MARIA.id()).deductibleMet()).isEqualTo(Money.of("460"));
    }

    @Test
    void clm5002DuplicateIsDeniedSoItNeverPaysTwice() {
        engine.adjudicate(CLM_5001);

        AdjudicationResult result = engine.adjudicate(CLM_5002);

        assertThat(result.status()).isEqualTo(ClaimStatus.DENIED);
        assertThat(result.reasonCode()).isEqualTo("DUPLICATE_CLAIM");
        assertThat(world.memberNow(MARIA.id()).deductibleMet()).isEqualTo(Money.of("460")); // unchanged
    }

    @Test
    void clm5003IsDeniedBecauseCoverageEnded() {
        AdjudicationResult result = engine.adjudicate(CLM_5003);

        assertThat(result.status()).isEqualTo(ClaimStatus.DENIED);
        assertThat(result.reasonCode()).isEqualTo("COVERAGE_TERMINATED");
        assertThat(result.breakdown()).isEmpty();
    }

    @Test
    void clm5004IsPendedAsTheFortiethVisitInOneDay() {
        for (Claim earlier : SampleData.quickCareEarlierVisits()) {
            world.member(SampleData.quickCarePatient(earlier.memberId()));
            engine.adjudicate(earlier);
        }
        world.member(SampleData.quickCarePatient(CLM_5004.memberId()));

        AdjudicationResult result = engine.adjudicate(CLM_5004);

        assertThat(result.status()).isEqualTo(ClaimStatus.PENDED);
        assertThat(result.reasonCode()).isEqualTo("PROVIDER_VISIT_LIMIT");
        assertThat(result.message()).contains("40 claims");
    }

    @Test
    void clm5005IsPendedNotLostWhenEligibilityIsDown() {
        world.eligibilityDown(true);

        AdjudicationResult result = engine.adjudicate(CLM_5005);

        assertThat(result.status()).isEqualTo(ClaimStatus.PENDED);
        assertThat(result.reasonCode()).isEqualTo("ELIGIBILITY_UNAVAILABLE");
    }

    @Test
    void clm5005IsPaidOnceEligibilityIsBack() {
        world.eligibilityDown(true);
        engine.adjudicate(CLM_5005);
        world.eligibilityDown(false);

        AdjudicationResult retry = engine.adjudicate(CLM_5005);

        assertThat(retry.status()).isEqualTo(ClaimStatus.PAID);
    }

    @Test
    void allFiveInArrivalOrder() {
        for (Claim earlier : SampleData.quickCareEarlierVisits()) {
            world.member(SampleData.quickCarePatient(earlier.memberId()));
            engine.adjudicate(earlier);
        }
        world.member(SampleData.quickCarePatient(CLM_5004.memberId()));

        List<AdjudicationResult> results = new ArrayList<>();
        results.add(engine.adjudicate(CLM_5001));
        results.add(engine.adjudicate(CLM_5002));
        results.add(engine.adjudicate(CLM_5003));
        results.add(engine.adjudicate(CLM_5004));
        world.eligibilityDown(true);
        results.add(engine.adjudicate(CLM_5005));

        System.out.println(table(results));
        assertThat(results).extracting(AdjudicationResult::status).containsExactly(
                ClaimStatus.PAID, ClaimStatus.DENIED, ClaimStatus.DENIED, ClaimStatus.PENDED, ClaimStatus.PENDED);
    }

    private static String table(List<AdjudicationResult> results) {
        StringBuilder out = new StringBuilder("\nClaim     Status   Allowed  Plan pays  Member pays  Reason\n");
        for (AdjudicationResult r : results) {
            String money = r.breakdown()
                    .map(b -> "%-8s %-10s %-12s".formatted(b.allowed(), b.planPays(), b.memberPays()))
                    .orElse("%-8s %-10s %-12s".formatted("-", "-", "-"));
            out.append("%-9s %-8s %s %s%n".formatted(r.claimId(), r.status(), money, r.reasonCode()));
        }
        return out.toString();
    }
}

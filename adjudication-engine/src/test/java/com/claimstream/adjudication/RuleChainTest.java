package com.claimstream.adjudication;

import static com.claimstream.domain.SampleData.CLM_5001;
import static org.assertj.core.api.Assertions.assertThat;

import com.claimstream.adjudication.rules.AdjudicationRule;
import com.claimstream.adjudication.rules.MedicalNecessityRule;
import com.claimstream.adjudication.rules.RuleChain;
import com.claimstream.adjudication.rules.RuleOutcome;
import com.claimstream.domain.Claim;
import com.claimstream.domain.ClaimId;
import com.claimstream.domain.ClaimLine;
import com.claimstream.domain.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RuleChainTest {

    @Test
    void stopsAtTheFirstRuleThatDecides() {
        List<String> ran = new ArrayList<>();
        AdjudicationRule passes = claim -> {
            ran.add("passes");
            return RuleOutcome.proceed();
        };
        AdjudicationRule denies = claim -> {
            ran.add("denies");
            return RuleOutcome.deny("TEST", "stop here");
        };
        AdjudicationRule neverRuns = claim -> {
            ran.add("neverRuns");
            return RuleOutcome.proceed();
        };

        RuleOutcome outcome = new RuleChain(List.of(passes, denies, neverRuns)).evaluate(CLM_5001);

        assertThat(outcome.reasonCode()).isEqualTo("TEST");
        assertThat(ran).containsExactly("passes", "denies");
    }

    @Test
    void emptyChainLetsTheClaimThrough() {
        assertThat(new RuleChain(List.of()).evaluate(CLM_5001).stopsTheChain()).isFalse();
    }

    @Test
    void medicalNecessityDeniesAnUncoveredService() {
        Claim cosmetic = new Claim(new ClaimId("CLM-5200"), CLM_5001.memberId(), CLM_5001.providerNpi(),
                CLM_5001.dateOfService(), List.of(new ClaimLine(1, "15780", Money.of("900"))));

        RuleOutcome outcome = new MedicalNecessityRule(Set.of("99213")).evaluate(cosmetic);

        assertThat(outcome.verdict()).isEqualTo(RuleOutcome.Verdict.DENY);
        assertThat(outcome.reasonCode()).isEqualTo("NOT_MEDICALLY_NECESSARY");
    }
}

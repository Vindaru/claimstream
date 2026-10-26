package com.claimstream.batch;

import static com.claimstream.domain.SampleData.CLM_5001;
import static com.claimstream.domain.SampleData.CLM_5003;
import static org.assertj.core.api.Assertions.assertThat;

import com.claimstream.adjudication.rules.EligibilityRule;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The running example goes through batch intake too. */
class SampleClaimsIntakeTest {

    @Test
    void clm5001IsAcceptedAndClm5003IsDeniedAtTheDoor() {
        ClaimIntake intake = new ClaimIntake(new EligibilityRule(Roster.standard()));

        intake.handle(ClaimLineParser.format(CLM_5001));
        intake.handle(ClaimLineParser.format(CLM_5003));
        intake.handle("CLM-5099|M-BROKEN");

        assertThat(intake.report().snapshot()).containsExactlyInAnyOrderEntriesOf(Map.of(
                BatchReport.ACCEPTED, 1L, "DENIED:COVERAGE_TERMINATED", 1L, BatchReport.MALFORMED, 1L));
    }
}

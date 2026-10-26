package com.claimstream.domain;

import static com.claimstream.domain.SampleData.CLM_5001;
import static com.claimstream.domain.SampleData.CLM_5002;
import static com.claimstream.domain.SampleData.DATE_OF_SERVICE;
import static com.claimstream.domain.SampleData.DR_PATEL;
import static com.claimstream.domain.SampleData.MARIA;
import static com.claimstream.domain.SampleData.OFFICE_VISIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClaimTest {

    @Test
    void clm5001BillsOneOfficeVisitFor150() {
        assertThat(CLM_5001.lines()).containsExactly(OFFICE_VISIT);
        assertThat(CLM_5001.totalBilled()).isEqualTo(Money.of("150"));
    }

    @Test
    void addsUpEveryLine() {
        Claim twoLines = new Claim(new ClaimId("CLM-5100"), MARIA.id(), DR_PATEL.npi(), DATE_OF_SERVICE,
                List.of(OFFICE_VISIT, new ClaimLine(2, "36415", Money.of("12.50"))));

        assertThat(twoLines.totalBilled()).isEqualTo(Money.of("162.50"));
    }

    @Test
    void callerCannotChangeLinesAfterTheClaimIsBuilt() {
        List<ClaimLine> lines = new ArrayList<>(List.of(OFFICE_VISIT));
        Claim claim = new Claim(new ClaimId("CLM-5101"), MARIA.id(), DR_PATEL.npi(), DATE_OF_SERVICE, lines);

        lines.add(new ClaimLine(2, "99214", Money.of("1000")));

        assertThat(claim.lines()).hasSize(1);
        assertThatThrownBy(() -> claim.lines().add(OFFICE_VISIT))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void duplicateLooksIdenticalButIsADifferentClaim() {
        assertThat(CLM_5002.memberId()).isEqualTo(CLM_5001.memberId());
        assertThat(CLM_5002.lines()).isEqualTo(CLM_5001.lines());
        assertThat(CLM_5002).isNotEqualTo(CLM_5001); // catching it is the rules engine's job (M2)
    }

    @Test
    void rejectsAClaimWithNoLines() {
        assertThatThrownBy(() -> new Claim(new ClaimId("CLM-5102"), MARIA.id(), DR_PATEL.npi(),
                DATE_OF_SERVICE, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

package com.claimstream.batch;

import static com.claimstream.domain.SampleData.CLM_5001;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.claimstream.domain.Claim;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ClaimLineParserTest {

    private final ClaimLineParser parser = new ClaimLineParser();

    @Test
    void parsesClm5001IntoTheSameClaimAsTheDomainModel() {
        Claim claim = parser.parse("CLM-5001|M-1001|1234567890|2026-10-20|99213|150.00");

        assertThat(claim).isEqualTo(CLM_5001);
        assertThat(claim.memberId()).isEqualTo(CLM_5001.memberId());
        assertThat(claim.providerNpi()).isEqualTo(CLM_5001.providerNpi());
        assertThat(claim.dateOfService()).isEqualTo(CLM_5001.dateOfService());
        assertThat(claim.lines()).isEqualTo(CLM_5001.lines());
    }

    @Test
    void formatIsTheReverseOfParse() {
        assertThat(ClaimLineParser.format(CLM_5001)).isEqualTo("CLM-5001|M-1001|1234567890|2026-10-20|99213|150.00");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "CLM-100009|M-BROKEN|1234567890",
        "CLM-5001|M-1001|1234567890|2026-10-20|99213|150.00|extra",
        "CLM-5001|M-1001|12345|2026-10-20|99213|150.00",
        "CLM-5001|M-1001|1234567890|20-10-2026|99213|150.00",
        "CLM-5001|M-1001|1234567890|2026-10-20|9921|150.00",
        "CLM-5001|M-1001|1234567890|2026-10-20|99213|lots"
    })
    void rejectsMalformedLines(String line) {
        assertThatThrownBy(() -> parser.parse(line)).isInstanceOf(MalformedLineException.class);
    }
}

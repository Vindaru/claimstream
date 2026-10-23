package com.claimstream.intake.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Plain unit test: no Spring, no Docker, runs in milliseconds. */
class PlatformInfoControllerTest {

    @Test
    void pipelineFollowsThePayerOrder() {
        PlatformInfoController.PlatformInfo info = new PlatformInfoController().platform();

        assertThat(info.pipeline())
                .containsExactly("SUBMISSION", "ELIGIBILITY", "PRICING", "ADJUDICATION", "PAYMENT", "REMITTANCE");
    }
}

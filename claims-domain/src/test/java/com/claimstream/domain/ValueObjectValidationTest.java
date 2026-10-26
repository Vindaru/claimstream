package com.claimstream.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/** Bad data is rejected at the door, so nothing downstream ever has to re-check it. */
class ValueObjectValidationTest {

    @ParameterizedTest
    @ValueSource(strings = {"123456789", "12345678901", "12345abcde", ""})
    void npiMustBeTenDigits(String bad) {
        assertThatThrownBy(() -> new Npi(bad)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1001", "m-1001", "M1001", "M-12"})
    void memberIdMustLookLikeM1001(String bad) {
        assertThatThrownBy(() -> new MemberId(bad)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"9921", "992134", "9921A"})
    void cptMustBeFiveDigits(String bad) {
        assertThatThrownBy(() -> new ClaimLine(1, bad, Money.of("150")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void planShareMustBeAFraction() {
        assertThatThrownBy(() -> new Plan("BAD", Money.of("500"), new BigDecimal("80")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(SampleData.GOLD_PPO.memberShare()).isEqualByComparingTo("0.20");
    }

    @Test
    void coverageCannotEndBeforeItStarts() {
        assertThatThrownBy(() -> new CoveragePeriod(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 30)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sameIdValueMeansSameId() {
        assertThat(new Npi("1234567890")).isEqualTo(SampleData.DR_PATEL.npi());
    }
}

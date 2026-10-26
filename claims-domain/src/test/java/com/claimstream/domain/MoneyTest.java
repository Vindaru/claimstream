package com.claimstream.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void addsExactlyWhereDoubleWouldDrift() {
        assertThat(0.1 + 0.2).isNotEqualTo(0.3);
        assertThat(Money.of("0.10").plus(Money.of("0.20"))).isEqualTo(Money.of("0.30"));
    }

    @Test
    void equalAmountsAreEqualWhateverTheScale() {
        assertThat(Money.of("150")).isEqualTo(Money.of("150.00"));
    }

    @Test
    void roundsRatesToTheCentUsingBankersRounding() {
        assertThat(Money.of("0.05").times(new BigDecimal("0.5"))).isEqualTo(Money.of("0.02"));
        assertThat(Money.of("0.15").times(new BigDecimal("0.5"))).isEqualTo(Money.of("0.08"));
    }

    @Test
    void refusesToGoNegative() {
        assertThatThrownBy(() -> Money.of("110").minus(Money.of("150")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void operationsReturnNewInstancesAndLeaveTheOriginalAlone() {
        Money billed = Money.of("150");

        Money writeOff = billed.minus(Money.of("110"));

        assertThat(writeOff).isEqualTo(Money.of("40"));
        assertThat(billed).isEqualTo(Money.of("150"));
    }

    @Test
    void printsAsDollars() {
        assertThat(Money.of("48")).hasToString("$48.00");
    }
}

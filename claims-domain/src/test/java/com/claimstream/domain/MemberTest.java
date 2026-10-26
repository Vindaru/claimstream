package com.claimstream.domain;

import static com.claimstream.domain.SampleData.DATE_OF_SERVICE;
import static com.claimstream.domain.SampleData.LAPSED_MEMBER;
import static com.claimstream.domain.SampleData.MARIA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MemberTest {

    @Test
    void mariaHas150OfHerDeductibleLeft() {
        assertThat(MARIA.deductibleRemaining()).isEqualTo(Money.of("150"));
    }

    @Test
    void mariaIsCoveredButTheLapsedMemberIsNot() {
        assertThat(MARIA.isCoveredOn(DATE_OF_SERVICE)).isTrue();
        assertThat(LAPSED_MEMBER.isCoveredOn(DATE_OF_SERVICE)).isFalse();
    }

    @Test
    void coverageEndDateIsInclusive() {
        assertThat(LAPSED_MEMBER.isCoveredOn(LAPSED_MEMBER.coverage().end())).isTrue();
        assertThat(LAPSED_MEMBER.isCoveredOn(LAPSED_MEMBER.coverage().end().plusDays(1))).isFalse();
    }

    @Test
    void applyingTheDeductibleReturnsANewMemberAndLeavesTheOriginalUnchanged() {
        Member after = MARIA.withDeductibleApplied(Money.of("110"));

        assertThat(after.deductibleMet()).isEqualTo(Money.of("460"));
        assertThat(MARIA.deductibleMet()).isEqualTo(Money.of("350"));
        assertThat(after).isEqualTo(MARIA); // same identity, different snapshot
    }

    @Test
    void cannotMeetMoreThanTheWholeDeductible() {
        assertThatThrownBy(() -> MARIA.withDeductibleApplied(Money.of("151")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds plan deductible");
    }
}

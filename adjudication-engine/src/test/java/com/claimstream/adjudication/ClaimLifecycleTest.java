package com.claimstream.adjudication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ClaimLifecycleTest {

    @Test
    void happyPathWalksEveryStatusInOrder() {
        ClaimLifecycle paid = ClaimLifecycle.received()
                .moveTo(ClaimStatus.VALIDATED)
                .moveTo(ClaimStatus.PRICED)
                .moveTo(ClaimStatus.ADJUDICATED)
                .moveTo(ClaimStatus.PAID);

        assertThat(paid.history()).containsExactly(ClaimStatus.RECEIVED, ClaimStatus.VALIDATED,
                ClaimStatus.PRICED, ClaimStatus.ADJUDICATED, ClaimStatus.PAID);
    }

    @Test
    void cannotSkipPricing() {
        assertThatThrownBy(() -> ClaimLifecycle.received().moveTo(ClaimStatus.VALIDATED).moveTo(ClaimStatus.PAID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Claim cannot move from VALIDATED to PAID");
    }

    @Test
    void paidAndDeniedAreFinal() {
        assertThat(ClaimStatus.PAID.next()).isEmpty();
        assertThat(ClaimStatus.DENIED.next()).isEmpty();
    }

    @Test
    void pendedClaimCanBeReDrivenFromTheStart() {
        assertThat(ClaimStatus.PENDED.canMoveTo(ClaimStatus.RECEIVED)).isTrue();
        assertThat(ClaimStatus.PENDED.canMoveTo(ClaimStatus.PAID)).isFalse();
    }

    @Test
    void movingReturnsANewLifecycle() {
        ClaimLifecycle received = ClaimLifecycle.received();

        received.moveTo(ClaimStatus.VALIDATED);

        assertThat(received.current()).isEqualTo(ClaimStatus.RECEIVED);
    }
}

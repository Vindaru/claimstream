package com.claimstream.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A benefit plan such as {@code GOLD-PPO}.
 *
 * @param code        plan code
 * @param deductible  amount the member pays each year before the plan shares costs
 * @param planShare   fraction the plan pays after the deductible, e.g. 0.80 for 80%
 */
public record Plan(String code, Money deductible, BigDecimal planShare) {

    public Plan {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(deductible, "deductible");
        Objects.requireNonNull(planShare, "planShare");
        if (planShare.signum() < 0 || planShare.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Plan share must be between 0 and 1: " + planShare);
        }
    }

    /** The member's coinsurance is whatever the plan doesn't pay: 1 - 0.80 = 0.20. */
    public BigDecimal memberShare() {
        return BigDecimal.ONE.subtract(planShare);
    }
}

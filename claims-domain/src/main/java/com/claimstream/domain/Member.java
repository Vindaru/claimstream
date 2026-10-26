package com.claimstream.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A person covered by a plan. An entity: identified by {@link MemberId}, not by its field values.
 *
 * <p>Immutable: applying a deductible returns a new Member with the higher accumulator,
 * so a half-finished calculation can never leave a shared Member in a wrong state.
 */
public record Member(MemberId id, String name, Plan plan, CoveragePeriod coverage, Money deductibleMet) {

    public Member {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(coverage, "coverage");
        Objects.requireNonNull(deductibleMet, "deductibleMet");
        if (deductibleMet.compareTo(plan.deductible()) > 0) {
            throw new IllegalArgumentException("Deductible met " + deductibleMet
                    + " exceeds plan deductible " + plan.deductible());
        }
    }

    public boolean isCoveredOn(LocalDate dateOfService) {
        return coverage.covers(dateOfService);
    }

    /** What Maria still has to pay herself before the plan starts sharing: $500 - $350 = $150. */
    public Money deductibleRemaining() {
        return plan.deductible().minus(deductibleMet);
    }

    /** Returns a copy of this member with {@code amount} more applied to the deductible. */
    public Member withDeductibleApplied(Money amount) {
        return new Member(id, name, plan, coverage, deductibleMet.plus(amount));
    }

    /** Entities compare by identity, so two snapshots of Maria are still Maria. */
    @Override
    public boolean equals(Object other) {
        return other instanceof Member that && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}

package com.claimstream.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * A provider's request to be paid for services given to a member on one date.
 *
 * <p>The claim refers to its member and provider by id only. That keeps it small, lets it travel
 * as a message between services later (M5), and means a claim never carries a stale copy of Maria.
 */
public record Claim(ClaimId id, MemberId memberId, Npi providerNpi, LocalDate dateOfService, List<ClaimLine> lines) {

    public Claim {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(memberId, "memberId");
        Objects.requireNonNull(providerNpi, "providerNpi");
        Objects.requireNonNull(dateOfService, "dateOfService");
        Objects.requireNonNull(lines, "lines");
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("A claim needs at least one line");
        }
        // Defensive copy: a caller who keeps the original list can't add lines behind our back.
        lines = List.copyOf(lines);
    }

    public Money totalBilled() {
        return lines.stream().map(ClaimLine::billed).reduce(Money.ZERO, Money::plus);
    }

    /** Entities compare by identity: CLM-5001 is CLM-5001 even if a field was corrected. */
    @Override
    public boolean equals(Object other) {
        return other instanceof Claim that && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}

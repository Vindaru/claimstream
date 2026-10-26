package com.claimstream.adjudication;

import com.claimstream.adjudication.benefits.BenefitBreakdown;
import com.claimstream.domain.ClaimId;
import java.util.Optional;

/**
 * The engine's answer for one claim: where it ended up, why, and (if paid) who pays what.
 *
 * @param benefits null unless the claim reached ADJUDICATED; read it through {@link #breakdown()}
 */
public record AdjudicationResult(ClaimId claimId, ClaimLifecycle lifecycle, String reasonCode, String message,
                                 BenefitBreakdown benefits) {

    static AdjudicationResult paid(ClaimId claimId, ClaimLifecycle lifecycle, BenefitBreakdown benefits) {
        return new AdjudicationResult(claimId, lifecycle, "", "", benefits);
    }

    static AdjudicationResult stopped(ClaimId claimId, ClaimLifecycle lifecycle, String reasonCode, String message) {
        return new AdjudicationResult(claimId, lifecycle, reasonCode, message, null);
    }

    public ClaimStatus status() {
        return lifecycle.current();
    }

    public Optional<BenefitBreakdown> breakdown() {
        return Optional.ofNullable(benefits);
    }
}

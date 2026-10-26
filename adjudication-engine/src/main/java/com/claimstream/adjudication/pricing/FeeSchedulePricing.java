package com.claimstream.adjudication.pricing;

import com.claimstream.domain.ClaimLine;
import com.claimstream.domain.Money;
import com.claimstream.domain.Provider;

/** In-network: the contracted rate, e.g. Dr. Patel's 99213 is allowed at $110. */
public final class FeeSchedulePricing implements PricingStrategy {

    private final FeeSchedule feeSchedule;

    public FeeSchedulePricing(FeeSchedule feeSchedule) {
        this.feeSchedule = feeSchedule;
    }

    @Override
    public Money allowedFor(ClaimLine line, Provider provider) {
        Money rate = feeSchedule.rateFor(provider.npi(), line.cptCode())
                .orElseThrow(() -> new NoFeeScheduleException(
                        "No contracted rate for CPT " + line.cptCode() + " with NPI " + provider.npi()));
        return rate.min(line.billed());
    }
}

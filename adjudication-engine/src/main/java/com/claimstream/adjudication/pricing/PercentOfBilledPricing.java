package com.claimstream.adjudication.pricing;

import com.claimstream.domain.ClaimLine;
import com.claimstream.domain.Money;
import com.claimstream.domain.Provider;
import java.math.BigDecimal;

/** Out-of-network: no contract, so the plan allows a fixed share of the billed charge, e.g. 60%. */
public final class PercentOfBilledPricing implements PricingStrategy {

    private final BigDecimal allowedShare;

    public PercentOfBilledPricing(BigDecimal allowedShare) {
        this.allowedShare = allowedShare;
    }

    @Override
    public Money allowedFor(ClaimLine line, Provider provider) {
        return line.billed().times(allowedShare);
    }
}

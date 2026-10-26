package com.claimstream.adjudication.pricing;

import com.claimstream.domain.ClaimLine;
import com.claimstream.domain.Money;
import com.claimstream.domain.Provider;

/** Strategy pattern: one interface, one class per way of working out what a service is worth. */
public interface PricingStrategy {

    /** The allowed amount for this line. Never more than what was billed. */
    Money allowedFor(ClaimLine line, Provider provider);
}

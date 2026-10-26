package com.claimstream.adjudication.pricing;

import com.claimstream.domain.Provider;

/**
 * Factory pattern: callers ask "how do I price this provider's claims?" and get the right strategy,
 * without knowing which classes exist. Adding a strategy (say, a capitated contract) changes only this class.
 */
public final class PricingStrategyFactory {

    private final PricingStrategy inNetwork;
    private final PricingStrategy outOfNetwork;

    public PricingStrategyFactory(PricingStrategy inNetwork, PricingStrategy outOfNetwork) {
        this.inNetwork = inNetwork;
        this.outOfNetwork = outOfNetwork;
    }

    public PricingStrategy forProvider(Provider provider) {
        return provider.inNetwork() ? inNetwork : outOfNetwork;
    }
}

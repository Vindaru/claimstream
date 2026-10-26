package com.claimstream.adjudication.pricing;

import com.claimstream.domain.Money;
import com.claimstream.domain.Npi;
import java.util.Optional;

/** Contracted rates: what an in-network provider agreed to accept for a CPT code. */
public interface FeeSchedule {

    Optional<Money> rateFor(Npi npi, String cptCode);
}

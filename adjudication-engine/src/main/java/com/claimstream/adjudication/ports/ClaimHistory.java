package com.claimstream.adjudication.ports;

import com.claimstream.adjudication.ClaimStatus;
import com.claimstream.domain.Claim;
import com.claimstream.domain.Npi;
import java.time.LocalDate;

/** Claims the engine has already seen. Backs the duplicate and visit-limit rules. */
public interface ClaimHistory {

    /** True if a claim for the same member, provider, date and services was already paid. */
    boolean hasPaidMatching(Claim claim);

    /** How many claims this provider has submitted for this date of service, whatever their outcome. */
    long countForProviderOn(Npi npi, LocalDate dateOfService);

    void record(Claim claim, ClaimStatus finalStatus);
}

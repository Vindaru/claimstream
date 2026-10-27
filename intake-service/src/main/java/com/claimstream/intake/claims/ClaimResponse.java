package com.claimstream.intake.claims;

import com.claimstream.domain.Claim;
import java.math.BigDecimal;

/** What the API answers. Stored with the idempotency key, so a retry gets exactly the same body back. */
public record ClaimResponse(String claimId, String memberId, String providerNpi, String status,
                            BigDecimal totalBilled) {

    static final String RECEIVED = "RECEIVED";

    static ClaimResponse received(Claim claim) {
        return new ClaimResponse(claim.id().value(), claim.memberId().value(), claim.providerNpi().value(),
                RECEIVED, claim.totalBilled().amount());
    }
}

package com.claimstream.intake.claims;

/** 409: this claim id is already stored under a different Idempotency-Key. */
public class DuplicateClaimException extends RuntimeException {

    public DuplicateClaimException(String claimId) {
        super("Claim " + claimId + " was already received");
    }
}

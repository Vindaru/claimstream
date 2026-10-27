package com.claimstream.intake.claims;

/**
 * The answer remembered for an idempotency key.
 *
 * @param requestHash SHA-256 of the request body, to catch the same key being reused for a different claim
 */
public record StoredResponse(String requestHash, int status, ClaimResponse body) {
}

package com.claimstream.intake.claims;

/** 409: another request with this Idempotency-Key is being processed right now. Retry shortly. */
public class RequestInProgressException extends RuntimeException {

    public RequestInProgressException(String key) {
        super("A request with Idempotency-Key " + key + " is already in progress");
    }
}

package com.claimstream.intake.claims;

/** 422: the key was already used for a different request body. Keys identify one request, never two. */
public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException(String key) {
        super("Idempotency-Key " + key + " was already used with a different request body");
    }
}

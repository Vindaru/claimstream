package com.claimstream.intake.claims;

/** 400: the body can't be turned into a valid claim. */
public class InvalidClaimException extends RuntimeException {

    public InvalidClaimException(String message) {
        super(message);
    }
}

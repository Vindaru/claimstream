package com.claimstream.adjudication.pricing;

/** An in-network provider billed a code with no contracted rate. A person must price it, so the claim pends. */
public class NoFeeScheduleException extends RuntimeException {

    public NoFeeScheduleException(String message) {
        super(message);
    }
}

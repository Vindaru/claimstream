package com.claimstream.adjudication.ports;

/** The eligibility source is down or timed out. The claim must be pended, never denied or lost (CLM-5005). */
public class EligibilityUnavailableException extends RuntimeException {

    public EligibilityUnavailableException(String message) {
        super(message);
    }
}

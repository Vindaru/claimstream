package com.claimstream.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/** Claim identifier such as {@code CLM-5001}. */
public record ClaimId(String value) {

    private static final Pattern FORMAT = Pattern.compile("CLM-\\d{4,}");

    public ClaimId {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Claim id must look like CLM-5001: " + value);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}

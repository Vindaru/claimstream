package com.claimstream.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/** National Provider Identifier: exactly 10 digits, e.g. {@code 1234567890}. */
public record Npi(String value) {

    private static final Pattern FORMAT = Pattern.compile("\\d{10}");

    public Npi {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("NPI must be 10 digits: " + value);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}

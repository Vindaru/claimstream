package com.claimstream.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/** Member identifier such as {@code M-1001}. A type of its own so it can't be mixed up with an NPI. */
public record MemberId(String value) {

    private static final Pattern FORMAT = Pattern.compile("M-\\d{4,}");

    public MemberId {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Member id must look like M-1001: " + value);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}

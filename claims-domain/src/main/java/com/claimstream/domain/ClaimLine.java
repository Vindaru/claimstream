package com.claimstream.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One billed service on a claim, e.g. line 1: CPT 99213, billed $150.
 *
 * <p>Value object: a line has no identity outside its claim.
 */
public record ClaimLine(int lineNumber, String cptCode, Money billed) {

    private static final Pattern CPT = Pattern.compile("\\d{5}");

    public ClaimLine {
        if (lineNumber < 1) {
            throw new IllegalArgumentException("Line numbers start at 1: " + lineNumber);
        }
        Objects.requireNonNull(cptCode, "cptCode");
        Objects.requireNonNull(billed, "billed");
        if (!CPT.matcher(cptCode).matches()) {
            throw new IllegalArgumentException("CPT code must be 5 digits: " + cptCode);
        }
        if (billed.isZero()) {
            throw new IllegalArgumentException("A billed line must charge something");
        }
    }
}

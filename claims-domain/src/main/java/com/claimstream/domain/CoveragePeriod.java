package com.claimstream.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * The dates a member is covered. {@code end} is inclusive; null means coverage is still open.
 */
public record CoveragePeriod(LocalDate start, LocalDate end) {

    public CoveragePeriod {
        Objects.requireNonNull(start, "start");
        if (end != null && end.isBefore(start)) {
            throw new IllegalArgumentException("Coverage cannot end before it starts: " + start + " to " + end);
        }
    }

    public static CoveragePeriod openFrom(LocalDate start) {
        return new CoveragePeriod(start, null);
    }

    public boolean covers(LocalDate date) {
        return !date.isBefore(start) && (end == null || !date.isAfter(end));
    }
}

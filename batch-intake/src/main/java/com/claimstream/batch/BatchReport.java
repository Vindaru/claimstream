package com.claimstream.batch;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Tally of what happened to every line, written by many threads at once.
 *
 * <p>Thread-safe without locks: {@link ConcurrentHashMap#computeIfAbsent} creates each counter exactly once,
 * and {@link LongAdder} spreads increments across cells so busy threads don't fight over one number.
 * A plain {@code HashMap<String, Integer>} here would silently lose counts (see {@link RaceConditionDemo}).
 */
public final class BatchReport {

    public static final String ACCEPTED = "ACCEPTED";
    public static final String MALFORMED = "MALFORMED";

    private final ConcurrentHashMap<String, LongAdder> counts = new ConcurrentHashMap<>();

    public void record(String outcome) {
        counts.computeIfAbsent(outcome, key -> new LongAdder()).increment();
    }

    public long count(String outcome) {
        LongAdder adder = counts.get(outcome);
        return adder == null ? 0 : adder.sum();
    }

    public long total() {
        return counts.values().stream().mapToLong(LongAdder::sum).sum();
    }

    /** A sorted, immutable snapshot, e.g. {ACCEPTED=98700, DENIED:COVERAGE_TERMINATED=1000, ...}. */
    public Map<String, Long> snapshot() {
        Map<String, Long> sorted = new TreeMap<>();
        counts.forEach((outcome, adder) -> sorted.put(outcome, adder.sum()));
        return Collections.unmodifiableMap(sorted);
    }
}

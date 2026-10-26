package com.claimstream.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * A non-negative US dollar amount, always held to the cent.
 *
 * <p>Value object: two Money instances with the same amount are equal, and every operation
 * returns a new instance instead of changing this one. BigDecimal, never double, because
 * 0.1 + 0.2 must equal 0.3 when we pay providers.
 */
public record Money(BigDecimal amount) implements Comparable<Money> {

    public static final Money ZERO = new Money(BigDecimal.ZERO);

    private static final int CENTS = 2;

    /** Compact constructor: the only way in, so every Money is valid. */
    public Money {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Money cannot be negative: " + amount);
        }
        amount = amount.setScale(CENTS, RoundingMode.HALF_EVEN);
    }

    public static Money of(String amount) {
        return new Money(new BigDecimal(amount));
    }

    public Money plus(Money other) {
        return new Money(amount.add(other.amount));
    }

    /** Subtracts, refusing to go below zero: a negative payment is a bug, not a number. */
    public Money minus(Money other) {
        return new Money(amount.subtract(other.amount));
    }

    /** Multiplies by a rate such as 0.80 for an 80% plan share, rounding to the cent. */
    public Money times(BigDecimal rate) {
        return new Money(amount.multiply(rate));
    }

    public Money min(Money other) {
        return compareTo(other) <= 0 ? this : other;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    @Override
    public int compareTo(Money other) {
        return amount.compareTo(other.amount);
    }

    @Override
    public String toString() {
        return "$" + amount.toPlainString();
    }
}

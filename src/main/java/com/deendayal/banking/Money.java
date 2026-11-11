package com.deendayal.banking;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable, non-negative monetary amount with two decimal places.
 * Over-precise input is rejected instead of silently rounded.
 */
public final class Money implements Comparable<Money> {

    private static final int SCALE = 2;

    public static final Money ZERO = Money.of("0");

    private final BigDecimal amount;

    private Money(BigDecimal amount) {
        this.amount = amount;
    }

    public static Money of(String value) {
        Objects.requireNonNull(value, "Amount must not be null");
        return of(new BigDecimal(value));
    }

    public static Money of(BigDecimal value) {
        Objects.requireNonNull(value, "Amount must not be null");
        if (value.signum() < 0) {
            throw new IllegalArgumentException("Amount must not be negative: " + value);
        }
        if (value.stripTrailingZeros().scale() > SCALE) {
            throw new IllegalArgumentException("Amount must have at most " + SCALE + " decimal places: " + value);
        }
        return new Money(value.setScale(SCALE));
    }

    public Money plus(Money other) {
        return new Money(amount.add(other.amount));
    }

    public Money minus(Money other) {
        return of(amount.subtract(other.amount));
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    @Override
    public int compareTo(Money other) {
        return amount.compareTo(other.amount);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Money other && amount.equals(other.amount);
    }

    @Override
    public int hashCode() {
        return amount.hashCode();
    }

    @Override
    public String toString() {
        return amount.toPlainString();
    }
}
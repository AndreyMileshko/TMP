package com.tmp.order.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Ordered quantity of an order item revision (Specification §5.3). Must be strictly positive.
 */
public final class OrderedQuantity {

    private final BigDecimal value;

    private OrderedQuantity(BigDecimal value) {
        this.value = value;
    }

    public static OrderedQuantity of(BigDecimal value) {
        Objects.requireNonNull(value, "value");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("Ordered quantity must be > 0: " + value);
        }
        return new OrderedQuantity(value);
    }

    public static OrderedQuantity of(long value) {
        return of(BigDecimal.valueOf(value));
    }

    public BigDecimal value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OrderedQuantity that)) {
            return false;
        }
        return value.compareTo(that.value) == 0;
    }

    @Override
    public int hashCode() {
        return value.stripTrailingZeros().hashCode();
    }

    @Override
    public String toString() {
        return value.toPlainString();
    }
}

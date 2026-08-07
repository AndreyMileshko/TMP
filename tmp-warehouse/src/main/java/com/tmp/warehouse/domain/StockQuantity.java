package com.tmp.warehouse.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Non-negative stock quantity. Negative quantity is forbidden by Warehouse invariants.
 */
public final class StockQuantity {

    private final BigDecimal value;

    private StockQuantity(BigDecimal value) {
        this.value = value;
    }

    public static StockQuantity of(BigDecimal value) {
        Objects.requireNonNull(value, "value");
        if (value.signum() < 0) {
            throw new IllegalArgumentException("Stock quantity must not be negative: " + value);
        }
        return new StockQuantity(value);
    }

    public static StockQuantity of(long value) {
        return of(BigDecimal.valueOf(value));
    }

    public static StockQuantity zero() {
        return of(BigDecimal.ZERO);
    }

    public BigDecimal value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StockQuantity that)) {
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

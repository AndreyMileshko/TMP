package com.tmp.production.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Whole-number product quantity for Production item state (Production Spec §5.2).
 *
 * <p>Ordered quantity must be strictly positive; launched, active and released quantities are
 * non-negative.
 */
public final class ProductionQuantity {

    private final BigDecimal value;

    private ProductionQuantity(BigDecimal value) {
        this.value = value;
    }

    public static ProductionQuantity positive(long value) {
        return positive(BigDecimal.valueOf(value));
    }

    public static ProductionQuantity positive(BigDecimal value) {
        Objects.requireNonNull(value, "value");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("Production quantity must be > 0: " + value);
        }
        requireWholeNumber(value);
        return new ProductionQuantity(value);
    }

    public static ProductionQuantity nonNegative(long value) {
        return nonNegative(BigDecimal.valueOf(value));
    }

    public static ProductionQuantity nonNegative(BigDecimal value) {
        Objects.requireNonNull(value, "value");
        if (value.signum() < 0) {
            throw new IllegalArgumentException("Production quantity must not be negative: " + value);
        }
        requireWholeNumber(value);
        return new ProductionQuantity(value);
    }

    public static ProductionQuantity zero() {
        return nonNegative(BigDecimal.ZERO);
    }

    public ProductionQuantity plus(ProductionQuantity other) {
        Objects.requireNonNull(other, "other");
        return nonNegative(value.add(other.value));
    }

    public ProductionQuantity minus(ProductionQuantity other) {
        Objects.requireNonNull(other, "other");
        return nonNegative(value.subtract(other.value));
    }

    public boolean isZero() {
        return value.signum() == 0;
    }

    public boolean isLessThanOrEqualTo(ProductionQuantity other) {
        Objects.requireNonNull(other, "other");
        return value.compareTo(other.value) <= 0;
    }

    public boolean isLessThan(ProductionQuantity other) {
        Objects.requireNonNull(other, "other");
        return value.compareTo(other.value) < 0;
    }

    public BigDecimal value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProductionQuantity that)) {
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

    private static void requireWholeNumber(BigDecimal value) {
        if (value.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException(
                    "Production quantity must be a whole number: " + value);
        }
    }
}

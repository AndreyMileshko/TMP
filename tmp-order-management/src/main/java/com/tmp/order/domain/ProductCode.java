package com.tmp.order.domain;

import java.util.Objects;

/**
 * Product code of an order item (Specification §5.2). Commercial field of the item, not a
 * Production or Warehouse identifier.
 */
public final class ProductCode {

    private final String value;

    private ProductCode(String value) {
        this.value = value;
    }

    public static ProductCode of(String value) {
        Objects.requireNonNull(value, "value");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Product code must not be blank");
        }
        return new ProductCode(trimmed);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProductCode that)) {
            return false;
        }
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}

package com.tmp.order.domain;

import java.util.Objects;

/**
 * Unique business number of a customer order (Specification §5.1). Uniqueness across orders is
 * enforced by the persistence / application layer; this value object validates non-blank form.
 */
public final class OrderNumber {

    private final String value;

    private OrderNumber(String value) {
        this.value = value;
    }

    public static OrderNumber of(String value) {
        Objects.requireNonNull(value, "value");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Order number must not be blank");
        }
        return new OrderNumber(trimmed);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OrderNumber that)) {
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

package com.tmp.order.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable, type-safe technical identity of a customer order.
 *
 * <p>Stable for the whole life of the order. Carries no commercial or production state.
 */
public final class OrderId {

    private final UUID value;

    private OrderId(UUID value) {
        this.value = value;
    }

    /**
     * Wraps an existing identifier value.
     *
     * @param value non-null UUID value
     * @return the order identifier
     */
    public static OrderId of(UUID value) {
        Objects.requireNonNull(value, "value");
        return new OrderId(value);
    }

    /**
     * Generates a new random order identifier.
     *
     * @return a fresh order identifier
     */
    public static OrderId generate() {
        return new OrderId(UUID.randomUUID());
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OrderId that)) {
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
        return value.toString();
    }
}

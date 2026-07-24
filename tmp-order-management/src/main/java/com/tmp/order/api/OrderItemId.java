package com.tmp.order.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable, type-safe technical identity of an order item.
 *
 * <p>Stable across all revisions of the item. Carries no commercial or production state.
 */
public final class OrderItemId {

    private final UUID value;

    private OrderItemId(UUID value) {
        this.value = value;
    }

    /**
     * Wraps an existing identifier value.
     *
     * @param value non-null UUID value
     * @return the order item identifier
     */
    public static OrderItemId of(UUID value) {
        Objects.requireNonNull(value, "value");
        return new OrderItemId(value);
    }

    /**
     * Generates a new random order item identifier.
     *
     * @return a fresh order item identifier
     */
    public static OrderItemId generate() {
        return new OrderItemId(UUID.randomUUID());
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OrderItemId that)) {
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

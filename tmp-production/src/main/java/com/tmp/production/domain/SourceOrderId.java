package com.tmp.production.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Production-owned opaque reference to a commercial customer order.
 *
 * <p>Maps from Order Management at application boundaries; carries no commercial lifecycle state.
 */
public final class SourceOrderId {

    private final UUID value;

    private SourceOrderId(UUID value) {
        this.value = value;
    }

    public static SourceOrderId of(UUID value) {
        Objects.requireNonNull(value, "value");
        return new SourceOrderId(value);
    }

    public static SourceOrderId generate() {
        return new SourceOrderId(UUID.randomUUID());
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SourceOrderId that)) {
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

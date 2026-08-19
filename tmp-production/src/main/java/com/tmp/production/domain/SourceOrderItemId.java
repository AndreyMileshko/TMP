package com.tmp.production.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Production-owned opaque reference to a commercial order item.
 *
 * <p>Stable across Order Item revisions. Production state is item-owned and never references
 * revision numbers.
 */
public final class SourceOrderItemId {

    private final UUID value;

    private SourceOrderItemId(UUID value) {
        this.value = value;
    }

    public static SourceOrderItemId of(UUID value) {
        Objects.requireNonNull(value, "value");
        return new SourceOrderItemId(value);
    }

    public static SourceOrderItemId generate() {
        return new SourceOrderItemId(UUID.randomUUID());
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SourceOrderItemId that)) {
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

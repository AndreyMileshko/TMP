package com.tmp.warehouse.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Technical identity of a warehouse.
 */
public final class WarehouseId {

    private final UUID value;

    private WarehouseId(UUID value) {
        this.value = value;
    }

    public static WarehouseId of(UUID value) {
        Objects.requireNonNull(value, "value");
        return new WarehouseId(value);
    }

    public static WarehouseId generate() {
        return new WarehouseId(UUID.randomUUID());
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WarehouseId that)) {
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

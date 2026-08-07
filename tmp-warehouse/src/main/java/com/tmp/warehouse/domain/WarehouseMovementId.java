package com.tmp.warehouse.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Technical identity of a warehouse movement history record.
 */
public final class WarehouseMovementId {

    private final UUID value;

    private WarehouseMovementId(UUID value) {
        this.value = value;
    }

    public static WarehouseMovementId of(UUID value) {
        Objects.requireNonNull(value, "value");
        return new WarehouseMovementId(value);
    }

    public static WarehouseMovementId generate() {
        return new WarehouseMovementId(UUID.randomUUID());
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WarehouseMovementId that)) {
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

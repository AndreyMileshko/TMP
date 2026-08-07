package com.tmp.warehouse.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Technical identity of a warehouse operation.
 */
public final class WarehouseOperationId {

    private final UUID value;

    private WarehouseOperationId(UUID value) {
        this.value = value;
    }

    public static WarehouseOperationId of(UUID value) {
        Objects.requireNonNull(value, "value");
        return new WarehouseOperationId(value);
    }

    public static WarehouseOperationId generate() {
        return new WarehouseOperationId(UUID.randomUUID());
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WarehouseOperationId that)) {
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

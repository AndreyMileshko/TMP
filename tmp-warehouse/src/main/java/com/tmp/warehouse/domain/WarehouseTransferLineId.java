package com.tmp.warehouse.domain;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of a Warehouse Transfer Document line. */
public final class WarehouseTransferLineId {

    private final UUID value;

    private WarehouseTransferLineId(UUID value) {
        this.value = value;
    }

    public static WarehouseTransferLineId of(UUID value) {
        Objects.requireNonNull(value, "value");
        return new WarehouseTransferLineId(value);
    }

    public static WarehouseTransferLineId generate() {
        return new WarehouseTransferLineId(UUID.randomUUID());
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WarehouseTransferLineId that)) {
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

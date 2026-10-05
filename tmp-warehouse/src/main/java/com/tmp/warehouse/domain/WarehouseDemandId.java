package com.tmp.warehouse.domain;

import java.util.Objects;
import java.util.UUID;

/** Technical identity of a Warehouse-owned Demand aggregate. */
public final class WarehouseDemandId {

    private final UUID value;

    private WarehouseDemandId(UUID value) {
        this.value = value;
    }

    public static WarehouseDemandId of(UUID value) {
        Objects.requireNonNull(value, "value");
        return new WarehouseDemandId(value);
    }

    public static WarehouseDemandId generate() {
        return new WarehouseDemandId(UUID.randomUUID());
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WarehouseDemandId that)) {
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

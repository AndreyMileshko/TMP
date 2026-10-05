package com.tmp.warehouse.domain;

import java.util.Objects;
import java.util.UUID;

/** Technical identity of a Warehouse Demand line. */
public final class WarehouseDemandLineId {

    private final UUID value;

    private WarehouseDemandLineId(UUID value) {
        this.value = value;
    }

    public static WarehouseDemandLineId of(UUID value) {
        Objects.requireNonNull(value, "value");
        return new WarehouseDemandLineId(value);
    }

    public static WarehouseDemandLineId generate() {
        return new WarehouseDemandLineId(UUID.randomUUID());
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WarehouseDemandLineId that)) {
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

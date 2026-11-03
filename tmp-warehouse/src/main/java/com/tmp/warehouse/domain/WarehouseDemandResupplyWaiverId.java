package com.tmp.warehouse.domain;

import java.util.Objects;
import java.util.UUID;

/** Identity of a Warehouse Demand resupply-waiver ledger event. */
public final class WarehouseDemandResupplyWaiverId {

    private final UUID value;

    private WarehouseDemandResupplyWaiverId(UUID value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    public static WarehouseDemandResupplyWaiverId of(UUID value) {
        Objects.requireNonNull(value, "value");
        return new WarehouseDemandResupplyWaiverId(value);
    }

    public static WarehouseDemandResupplyWaiverId generate() {
        return new WarehouseDemandResupplyWaiverId(UUID.randomUUID());
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WarehouseDemandResupplyWaiverId that)) {
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
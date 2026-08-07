package com.tmp.warehouse.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Technical identity of a stock position row.
 *
 * <p>Business uniqueness remains {@code Warehouse + Storage Cell + Material + Stock State}
 * (Specification §6.1); this id is the stable persistence key.
 */
public final class StockPositionId {

    private final UUID value;

    private StockPositionId(UUID value) {
        this.value = value;
    }

    public static StockPositionId of(UUID value) {
        Objects.requireNonNull(value, "value");
        return new StockPositionId(value);
    }

    public static StockPositionId generate() {
        return new StockPositionId(UUID.randomUUID());
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StockPositionId that)) {
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

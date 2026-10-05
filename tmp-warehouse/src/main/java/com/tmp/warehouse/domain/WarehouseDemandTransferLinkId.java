package com.tmp.warehouse.domain;

import java.util.Objects;
import java.util.UUID;

/** Technical identity of a Demand line ↔ Transfer line link. */
public final class WarehouseDemandTransferLinkId {

    private final UUID value;

    private WarehouseDemandTransferLinkId(UUID value) {
        this.value = value;
    }

    public static WarehouseDemandTransferLinkId of(UUID value) {
        Objects.requireNonNull(value, "value");
        return new WarehouseDemandTransferLinkId(value);
    }

    public static WarehouseDemandTransferLinkId generate() {
        return new WarehouseDemandTransferLinkId(UUID.randomUUID());
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WarehouseDemandTransferLinkId that)) {
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

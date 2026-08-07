package com.tmp.warehouse.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Technical identity of a storage cell.
 */
public final class StorageCellId {

    private final UUID value;

    private StorageCellId(UUID value) {
        this.value = value;
    }

    public static StorageCellId of(UUID value) {
        Objects.requireNonNull(value, "value");
        return new StorageCellId(value);
    }

    public static StorageCellId generate() {
        return new StorageCellId(UUID.randomUUID());
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StorageCellId that)) {
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

package com.tmp.warehouse.domain;

import java.util.Objects;
import java.util.UUID;

/** Technical identity of a warehouse-owned material reference row. */
public final class MaterialReferenceId {

    private final UUID value;

    private MaterialReferenceId(UUID value) {
        this.value = value;
    }

    public static MaterialReferenceId of(UUID value) {
        Objects.requireNonNull(value, "value");
        return new MaterialReferenceId(value);
    }

    public static MaterialReferenceId generate() {
        return new MaterialReferenceId(UUID.randomUUID());
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MaterialReferenceId that)) {
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

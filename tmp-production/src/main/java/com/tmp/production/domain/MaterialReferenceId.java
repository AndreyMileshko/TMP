package com.tmp.production.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Opaque Production-owned reference to a Warehouse Material Reference.
 *
 * <p>Production does not own, create, or mutate Material Reference; this identity is an external
 * reference without a cross-capability FK.
 */
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

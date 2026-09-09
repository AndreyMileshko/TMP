package com.tmp.production.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Production-owned identity of a Material Requirement.
 *
 * <p>Not a Warehouse Transfer ID, Document Engine document ID, or Order ID.
 */
public final class MaterialRequirementId {

    private final UUID value;

    private MaterialRequirementId(UUID value) {
        this.value = value;
    }

    public static MaterialRequirementId of(UUID value) {
        Objects.requireNonNull(value, "value");
        return new MaterialRequirementId(value);
    }

    public static MaterialRequirementId generate() {
        return new MaterialRequirementId(UUID.randomUUID());
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MaterialRequirementId that)) {
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

package com.tmp.production.domain;

import java.util.Objects;
import java.util.UUID;

/** Production-owned identity of one Material Requirement line. */
public final class MaterialRequirementLineId {

    private final UUID value;

    private MaterialRequirementLineId(UUID value) {
        this.value = value;
    }

    public static MaterialRequirementLineId of(UUID value) {
        Objects.requireNonNull(value, "value");
        return new MaterialRequirementLineId(value);
    }

    public static MaterialRequirementLineId generate() {
        return new MaterialRequirementLineId(UUID.randomUUID());
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MaterialRequirementLineId that)) {
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

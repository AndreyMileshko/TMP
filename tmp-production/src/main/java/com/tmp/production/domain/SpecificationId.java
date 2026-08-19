package com.tmp.production.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Stable opaque Production Specification Reference fixed at Launch.
 *
 * <p>Addresses a specific immutable Specification in Order Management. Production does not store
 * specification content and does not depend on Order Item Revision.
 */
public final class SpecificationId {

    private final UUID value;

    private SpecificationId(UUID value) {
        this.value = value;
    }

    public static SpecificationId of(UUID value) {
        Objects.requireNonNull(value, "value");
        return new SpecificationId(value);
    }

    public static SpecificationId generate() {
        return new SpecificationId(UUID.randomUUID());
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SpecificationId that)) {
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

package com.tmp.order.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Stable opaque identifier for an immutable item specification (OM Specification v1.10).
 *
 * <p>Production and other external consumers reference specifications by this identifier
 * without needing Order Item Revision semantics.
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

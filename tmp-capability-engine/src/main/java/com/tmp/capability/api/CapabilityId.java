package com.tmp.capability.api;

import java.util.Objects;

/**
 * Immutable, technical identity of a Capability. Carries no business meaning.
 */
public final class CapabilityId {

    private final String value;

    private CapabilityId(String value) {
        this.value = value;
    }

    public static CapabilityId of(String value) {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Capability id must not be blank");
        }
        return new CapabilityId(value);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CapabilityId that)) {
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
        return value;
    }
}

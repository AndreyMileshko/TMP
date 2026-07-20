package com.tmp.core.api.capability;

import java.util.Objects;

/**
 * Metadata descriptor for a registered capability.
 * Platform Core stores descriptors only; it does not load capability internals.
 */
public final class CapabilityDescriptor {

    private final String id;
    private final String name;
    private final String version;

    public CapabilityDescriptor(String id, String name, String version) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.version = Objects.requireNonNull(version, "version");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String version() {
        return version;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CapabilityDescriptor that)) {
            return false;
        }
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "CapabilityDescriptor{id='" + id + "', name='" + name + "', version='" + version + "'}";
    }
}

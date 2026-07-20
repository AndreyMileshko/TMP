package com.tmp.core.api.component;

import java.util.Objects;

/**
 * Immutable metadata describing a registered platform component.
 */
public final class PlatformComponentMetadata {

    private final String id;
    private final String name;
    private final String version;
    private final ComponentType type;

    public PlatformComponentMetadata(String id, String name, String version, ComponentType type) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.version = Objects.requireNonNull(version, "version");
        this.type = Objects.requireNonNull(type, "type");
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

    public ComponentType type() {
        return type;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PlatformComponentMetadata that)) {
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
        return "PlatformComponentMetadata{id='" + id + "', name='" + name + "', version='" + version
                + "', type=" + type + '}';
    }
}

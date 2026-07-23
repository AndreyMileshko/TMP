package com.tmp.security.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable technical identity of a Security role. Carries no password or credential data.
 */
public final class RoleId {

    private final UUID value;

    private RoleId(UUID value) {
        this.value = value;
    }

    public static RoleId of(UUID value) {
        Objects.requireNonNull(value, "value");
        return new RoleId(value);
    }

    public static RoleId generate() {
        return new RoleId(UUID.randomUUID());
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RoleId that)) {
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

package com.tmp.security.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable technical identity of an in-memory user session. Carries no password or credential data.
 */
public final class SessionId {

    private final UUID value;

    private SessionId(UUID value) {
        this.value = value;
    }

    public static SessionId of(UUID value) {
        Objects.requireNonNull(value, "value");
        return new SessionId(value);
    }

    public static SessionId generate() {
        return new SessionId(UUID.randomUUID());
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SessionId that)) {
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

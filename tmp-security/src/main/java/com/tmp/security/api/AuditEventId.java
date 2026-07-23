package com.tmp.security.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable technical identity of a Security audit event. Carries no password or credential data.
 */
public final class AuditEventId {

    private final UUID value;

    private AuditEventId(UUID value) {
        this.value = value;
    }

    public static AuditEventId of(UUID value) {
        Objects.requireNonNull(value, "value");
        return new AuditEventId(value);
    }

    public static AuditEventId generate() {
        return new AuditEventId(UUID.randomUUID());
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AuditEventId that)) {
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

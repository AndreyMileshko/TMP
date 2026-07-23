package com.tmp.security.domain;

import com.tmp.security.api.PermissionId;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Registered permission definition. {@link PermissionId} is immutable after registration;
 * only metadata and active flag may evolve.
 */
public final class PermissionDefinition {

    private final PermissionId permissionId;
    private final String displayName;
    private final String description;
    private final boolean active;
    private final Instant registeredAt;
    private final long version;

    private PermissionDefinition(
            PermissionId permissionId,
            String displayName,
            String description,
            boolean active,
            Instant registeredAt,
            long version) {
        this.permissionId = Objects.requireNonNull(permissionId, "permissionId");
        this.displayName = requireNonBlank(displayName, "displayName");
        this.description = description == null ? "" : description;
        this.active = active;
        this.registeredAt = Objects.requireNonNull(registeredAt, "registeredAt");
        this.version = version;
    }

    public static PermissionDefinition register(
            PermissionId permissionId, String displayName, String description, Clock clock) {
        Objects.requireNonNull(clock, "clock");
        return new PermissionDefinition(
                permissionId, displayName, description, true, clock.instant(), 0L);
    }

    /**
     * Rehydrates a persisted definition. Used by persistence adapters only.
     */
    public static PermissionDefinition rehydrate(
            PermissionId permissionId,
            String displayName,
            String description,
            boolean active,
            Instant registeredAt,
            long version) {
        return new PermissionDefinition(
                permissionId, displayName, description, active, registeredAt, version);
    }

    public PermissionDefinition withDisplayName(String newDisplayName) {
        return new PermissionDefinition(
                permissionId, newDisplayName, description, active, registeredAt, version);
    }

    public PermissionDefinition withDescription(String newDescription) {
        return new PermissionDefinition(
                permissionId, displayName, newDescription, active, registeredAt, version);
    }

    public PermissionDefinition activated() {
        if (active) {
            return this;
        }
        return new PermissionDefinition(
                permissionId, displayName, description, true, registeredAt, version);
    }

    public PermissionDefinition deactivated() {
        if (!active) {
            return this;
        }
        return new PermissionDefinition(
                permissionId, displayName, description, false, registeredAt, version);
    }

    public PermissionId permissionId() {
        return permissionId;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public boolean active() {
        return active;
    }

    public Instant registeredAt() {
        return registeredAt;
    }

    public long version() {
        return version;
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}

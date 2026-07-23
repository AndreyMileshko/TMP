package com.tmp.security.domain;

import com.tmp.security.api.PermissionId;
import com.tmp.security.api.RoleId;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable role aggregate: a named permission-set template.
 */
public final class Role {

    private final RoleId id;
    private final String name;
    private final String description;
    private final Set<PermissionId> permissions;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Role(
            RoleId id,
            String name,
            String description,
            Set<PermissionId> permissions,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = requireNonBlank(name, "name");
        this.description = description == null ? "" : description;
        this.permissions = Collections.unmodifiableSet(new LinkedHashSet<>(permissions));
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static Role create(RoleId id, String name, String description, Clock clock) {
        Objects.requireNonNull(clock, "clock");
        Instant now = clock.instant();
        return new Role(id, name, description, Set.of(), 0L, now, now);
    }

    /**
     * Rehydrates a persisted role. Used by persistence adapters only.
     */
    public static Role rehydrate(
            RoleId id,
            String name,
            String description,
            Set<PermissionId> permissions,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        return new Role(id, name, description, permissions, version, createdAt, updatedAt);
    }

    public Role withName(String newName, Clock clock) {
        Objects.requireNonNull(clock, "clock");
        return new Role(id, newName, description, permissions, version, createdAt, clock.instant());
    }

    public Role withDescription(String newDescription, Clock clock) {
        Objects.requireNonNull(clock, "clock");
        return new Role(id, name, newDescription, permissions, version, createdAt, clock.instant());
    }

    public Role grantPermission(PermissionId permissionId, Clock clock) {
        Objects.requireNonNull(permissionId, "permissionId");
        Objects.requireNonNull(clock, "clock");
        if (permissions.contains(permissionId)) {
            return this;
        }
        Set<PermissionId> next = new LinkedHashSet<>(permissions);
        next.add(permissionId);
        return new Role(id, name, description, next, version, createdAt, clock.instant());
    }

    public Role revokePermission(PermissionId permissionId, Clock clock) {
        Objects.requireNonNull(permissionId, "permissionId");
        Objects.requireNonNull(clock, "clock");
        if (!permissions.contains(permissionId)) {
            return this;
        }
        Set<PermissionId> next = new LinkedHashSet<>(permissions);
        next.remove(permissionId);
        return new Role(id, name, description, next, version, createdAt, clock.instant());
    }

    public RoleId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public Set<PermissionId> permissions() {
        return permissions;
    }

    public long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}

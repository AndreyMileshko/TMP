package com.tmp.security.api;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Display-safe role summary. Never carries credential material.
 */
public final class RoleSummary {

    private final RoleId id;
    private final String name;
    private final String description;
    private final Set<PermissionId> permissionIds;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;

    public RoleSummary(
            RoleId id,
            String name,
            String description,
            Set<PermissionId> permissionIds,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.description = Objects.requireNonNull(description, "description");
        this.permissionIds = Set.copyOf(Objects.requireNonNull(permissionIds, "permissionIds"));
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
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

    public Set<PermissionId> permissionIds() {
        return permissionIds;
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

    @Override
    public String toString() {
        return "RoleSummary{id=" + id + ", name=" + name + "}";
    }
}

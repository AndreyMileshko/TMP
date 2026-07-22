package com.tmp.capability.api;

import java.util.Objects;

/**
 * Immutable, domain-independent description of a permission that a Capability declares
 * as existing. This is a pure metadata contract: it carries no user, role, assignment,
 * or access-check logic — those concerns belong to Stage 4 (Security).
 */
public final class PermissionDescriptor {

    private final String permissionId;
    private final String displayName;
    private final String description;

    private PermissionDescriptor(String permissionId, String displayName, String description) {
        this.permissionId = permissionId;
        this.displayName = displayName;
        this.description = description;
    }

    public static PermissionDescriptor of(String permissionId, String displayName, String description) {
        requireNonBlank(permissionId, "permissionId");
        requireNonBlank(displayName, "displayName");
        Objects.requireNonNull(description, "description");
        return new PermissionDescriptor(permissionId, displayName, description);
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    public String permissionId() {
        return permissionId;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PermissionDescriptor that)) {
            return false;
        }
        return permissionId.equals(that.permissionId);
    }

    @Override
    public int hashCode() {
        return permissionId.hashCode();
    }

    @Override
    public String toString() {
        return permissionId;
    }
}

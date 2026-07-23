package com.tmp.security.api;

import java.util.Objects;

/**
 * Display-safe permission definition summary.
 */
public final class PermissionSummary {

    private final PermissionId permissionId;
    private final String displayName;
    private final String description;
    private final boolean active;

    public PermissionSummary(
            PermissionId permissionId, String displayName, String description, boolean active) {
        this.permissionId = Objects.requireNonNull(permissionId, "permissionId");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.description = Objects.requireNonNull(description, "description");
        this.active = active;
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

    @Override
    public String toString() {
        return "PermissionSummary{permissionId=" + permissionId + ", active=" + active + "}";
    }
}

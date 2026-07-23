package com.tmp.security.domain;

import com.tmp.security.api.RoleId;
import com.tmp.security.api.UserId;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable pairing of a user and an assigned role.
 */
public final class RoleAssignment {

    private final UserId userId;
    private final RoleId roleId;
    private final Instant assignedAt;

    private RoleAssignment(UserId userId, RoleId roleId, Instant assignedAt) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.roleId = Objects.requireNonNull(roleId, "roleId");
        this.assignedAt = Objects.requireNonNull(assignedAt, "assignedAt");
    }

    public static RoleAssignment of(UserId userId, RoleId roleId, Instant assignedAt) {
        return new RoleAssignment(userId, roleId, assignedAt);
    }

    public UserId userId() {
        return userId;
    }

    public RoleId roleId() {
        return roleId;
    }

    public Instant assignedAt() {
        return assignedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RoleAssignment that)) {
            return false;
        }
        return userId.equals(that.userId) && roleId.equals(that.roleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, roleId);
    }
}

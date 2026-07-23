package com.tmp.security.domain;

import com.tmp.security.api.PermissionId;
import com.tmp.security.api.UserId;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Individual GRANT/REVOKE override for one user and one permission.
 */
public final class IndividualPermissionOverride {

    private final UserId userId;
    private final PermissionId permissionId;
    private final PermissionOverrideDecision decision;
    private final Instant updatedAt;
    private final long version;

    private IndividualPermissionOverride(
            UserId userId,
            PermissionId permissionId,
            PermissionOverrideDecision decision,
            Instant updatedAt,
            long version) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.permissionId = Objects.requireNonNull(permissionId, "permissionId");
        this.decision = Objects.requireNonNull(decision, "decision");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = version;
    }

    public static IndividualPermissionOverride of(
            UserId userId,
            PermissionId permissionId,
            PermissionOverrideDecision decision,
            Clock clock) {
        Objects.requireNonNull(clock, "clock");
        return new IndividualPermissionOverride(userId, permissionId, decision, clock.instant(), 0L);
    }

    /**
     * Rehydrates a persisted override. Used by persistence adapters only.
     */
    public static IndividualPermissionOverride rehydrate(
            UserId userId,
            PermissionId permissionId,
            PermissionOverrideDecision decision,
            Instant updatedAt,
            long version) {
        return new IndividualPermissionOverride(userId, permissionId, decision, updatedAt, version);
    }

    public IndividualPermissionOverride withDecision(PermissionOverrideDecision newDecision, Clock clock) {
        Objects.requireNonNull(newDecision, "newDecision");
        Objects.requireNonNull(clock, "clock");
        return new IndividualPermissionOverride(userId, permissionId, newDecision, clock.instant(), version);
    }

    public UserId userId() {
        return userId;
    }

    public PermissionId permissionId() {
        return permissionId;
    }

    public PermissionOverrideDecision decision() {
        return decision;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public long version() {
        return version;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof IndividualPermissionOverride that)) {
            return false;
        }
        return userId.equals(that.userId) && permissionId.equals(that.permissionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, permissionId);
    }
}

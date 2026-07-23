package com.tmp.security.domain;

import com.tmp.security.api.PermissionId;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Registered permission definition. {@link PermissionId} is immutable after registration;
 * only metadata, active flag, and owner capability may evolve via controlled mutators.
 */
public final class PermissionDefinition {

    /**
     * Owner written by Flyway V5 for permission definitions that existed before ownership
     * was introduced. Claimable once via {@link #claimLegacyOwnership(String)}.
     */
    public static final String LEGACY_UNASSIGNED_OWNER = "legacy.unassigned";

    private final PermissionId permissionId;
    private final String ownerCapabilityId;
    private final String displayName;
    private final String description;
    private final boolean active;
    private final Instant registeredAt;
    private final long version;

    private PermissionDefinition(
            PermissionId permissionId,
            String ownerCapabilityId,
            String displayName,
            String description,
            boolean active,
            Instant registeredAt,
            long version) {
        this.permissionId = Objects.requireNonNull(permissionId, "permissionId");
        this.ownerCapabilityId = requireNonBlank(ownerCapabilityId, "ownerCapabilityId");
        this.displayName = requireNonBlank(displayName, "displayName");
        this.description = description == null ? "" : description;
        this.active = active;
        this.registeredAt = Objects.requireNonNull(registeredAt, "registeredAt");
        this.version = version;
    }

    public static PermissionDefinition register(
            PermissionId permissionId,
            String ownerCapabilityId,
            String displayName,
            String description,
            Clock clock) {
        Objects.requireNonNull(clock, "clock");
        return new PermissionDefinition(
                permissionId, ownerCapabilityId, displayName, description, true, clock.instant(), 0L);
    }

    /**
     * Rehydrates a persisted definition. Used by persistence adapters only.
     */
    public static PermissionDefinition rehydrate(
            PermissionId permissionId,
            String ownerCapabilityId,
            String displayName,
            String description,
            boolean active,
            Instant registeredAt,
            long version) {
        return new PermissionDefinition(
                permissionId, ownerCapabilityId, displayName, description, active, registeredAt, version);
    }

    public PermissionDefinition withDisplayName(String newDisplayName) {
        return new PermissionDefinition(
                permissionId, ownerCapabilityId, newDisplayName, description, active, registeredAt, version);
    }

    public PermissionDefinition withDescription(String newDescription) {
        return new PermissionDefinition(
                permissionId, ownerCapabilityId, displayName, newDescription, active, registeredAt, version);
    }

    public PermissionDefinition activated() {
        if (active) {
            return this;
        }
        return new PermissionDefinition(
                permissionId, ownerCapabilityId, displayName, description, true, registeredAt, version);
    }

    public PermissionDefinition deactivated() {
        if (!active) {
            return this;
        }
        return new PermissionDefinition(
                permissionId, ownerCapabilityId, displayName, description, false, registeredAt, version);
    }

    /**
     * One-time acceptance of ownership for definitions backfilled as
     * {@link #LEGACY_UNASSIGNED_OWNER}. After a real Capability owns the definition,
     * further ownership mismatches must be rejected by synchronization.
     *
     * @throws PermissionOwnershipConflictException if the current owner is not legacy-unassigned
     */
    public PermissionDefinition claimLegacyOwnership(String capabilityId) {
        String claimedOwner = requireNonBlank(capabilityId, "capabilityId");
        if (!LEGACY_UNASSIGNED_OWNER.equals(ownerCapabilityId)) {
            throw new PermissionOwnershipConflictException(
                    "Permission '"
                            + permissionId.value()
                            + "' cannot claim legacy ownership; current owner is '"
                            + ownerCapabilityId
                            + "'");
        }
        return new PermissionDefinition(
                permissionId, claimedOwner, displayName, description, active, registeredAt, version);
    }

    public PermissionId permissionId() {
        return permissionId;
    }

    public String ownerCapabilityId() {
        return ownerCapabilityId;
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

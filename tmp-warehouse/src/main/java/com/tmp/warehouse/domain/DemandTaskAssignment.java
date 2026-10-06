package com.tmp.warehouse.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Informational current worker for a Demand-backed production material supply task (B3B-3C1).
 *
 * <p>Not a business document. Presence of an assignment means task state IN_WORK; absence means
 * NEW. Security {@code workingUserId} is an opaque UUID (no Warehouse→Security FK).
 */
public final class DemandTaskAssignment {

    private final UUID demandId;
    private final UUID workingUserId;
    private final Instant workingSince;
    private final Instant updatedAt;

    private DemandTaskAssignment(
            UUID demandId, UUID workingUserId, Instant workingSince, Instant updatedAt) {
        this.demandId = Objects.requireNonNull(demandId, "demandId");
        this.workingUserId = Objects.requireNonNull(workingUserId, "workingUserId");
        this.workingSince = Objects.requireNonNull(workingSince, "workingSince");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static DemandTaskAssignment of(
            UUID demandId, UUID workingUserId, Instant workingSince, Instant updatedAt) {
        return new DemandTaskAssignment(demandId, workingUserId, workingSince, updatedAt);
    }

    public UUID demandId() {
        return demandId;
    }

    public UUID workingUserId() {
        return workingUserId;
    }

    public Instant workingSince() {
        return workingSince;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}

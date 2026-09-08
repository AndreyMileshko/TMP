package com.tmp.warehouse.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Informational current worker for a DRAFT Transfer Document preparation task (Stage 3.5.5).
 *
 * <p>Not a business document. Presence of an assignment means task state IN_WORK; absence means
 * NEW. Security {@code workingUserId} is an opaque UUID (no Warehouse→Security FK).
 */
public final class TransferTaskAssignment {

    private final UUID documentId;
    private final UUID workingUserId;
    private final Instant workingSince;
    private final Instant updatedAt;

    private TransferTaskAssignment(
            UUID documentId, UUID workingUserId, Instant workingSince, Instant updatedAt) {
        this.documentId = Objects.requireNonNull(documentId, "documentId");
        this.workingUserId = Objects.requireNonNull(workingUserId, "workingUserId");
        this.workingSince = Objects.requireNonNull(workingSince, "workingSince");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static TransferTaskAssignment of(
            UUID documentId, UUID workingUserId, Instant workingSince, Instant updatedAt) {
        return new TransferTaskAssignment(documentId, workingUserId, workingSince, updatedAt);
    }

    public UUID documentId() {
        return documentId;
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

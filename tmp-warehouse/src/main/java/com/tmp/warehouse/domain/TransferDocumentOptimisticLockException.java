package com.tmp.warehouse.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Thrown when a Warehouse Transfer Document payload update fails optimistic locking
 * ({@code payload_revision} CAS).
 */
public final class TransferDocumentOptimisticLockException extends RuntimeException {

    private final UUID documentId;
    private final long expectedRevision;
    private final long actualRevision;

    public TransferDocumentOptimisticLockException(
            UUID documentId, long expectedRevision, long actualRevision) {
        super(
                "Transfer document payload revision mismatch: documentId="
                        + documentId
                        + ", expected="
                        + expectedRevision
                        + ", actual="
                        + actualRevision);
        this.documentId = Objects.requireNonNull(documentId, "documentId");
        this.expectedRevision = expectedRevision;
        this.actualRevision = actualRevision;
    }

    public UUID documentId() {
        return documentId;
    }

    public long expectedRevision() {
        return expectedRevision;
    }

    public long actualRevision() {
        return actualRevision;
    }
}

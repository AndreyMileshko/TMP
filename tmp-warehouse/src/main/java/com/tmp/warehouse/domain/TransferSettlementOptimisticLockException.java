package com.tmp.warehouse.domain;

import java.util.UUID;

/** Optimistic lock failure on Transfer Document settlement operational revision. */
public final class TransferSettlementOptimisticLockException extends RuntimeException {

    private final UUID documentId;
    private final long expectedRevision;
    private final long actualRevision;

    public TransferSettlementOptimisticLockException(
            UUID documentId, long expectedRevision, long actualRevision) {
        super(
                "Stale operational revision for transfer settlement: documentId="
                        + documentId
                        + ", expected="
                        + expectedRevision
                        + ", actual="
                        + actualRevision);
        this.documentId = documentId;
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

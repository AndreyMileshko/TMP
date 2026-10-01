package com.tmp.production.domain;

/** Raised when an order quantity mode is changed with a stale version. */
public final class OrderQuantityModeOptimisticLockException extends RuntimeException {

    private final SourceOrderId sourceOrderId;
    private final long expectedVersion;

    public OrderQuantityModeOptimisticLockException(
            SourceOrderId sourceOrderId, long expectedVersion) {
        super(
                "Production quantity mode of order "
                        + sourceOrderId
                        + " was changed by another user (expectedVersion="
                        + expectedVersion
                        + "); reload and retry");
        this.sourceOrderId = sourceOrderId;
        this.expectedVersion = expectedVersion;
    }

    public SourceOrderId sourceOrderId() {
        return sourceOrderId;
    }

    public long expectedVersion() {
        return expectedVersion;
    }
}

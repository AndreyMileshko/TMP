package com.tmp.production.domain;

import java.util.Objects;

/**
 * Computed Order Production View (Production Spec §5.3). Recalculated on each query; never stored.
 */
public final class OrderProductionView {

    private final SourceOrderId sourceOrderId;
    private final OrderProductionViewStatus status;
    private final int itemCount;
    private final int inProductionCount;
    private final int partiallyReleasedCount;
    private final int releasedCount;
    private final int cancelledCount;

    public OrderProductionView(
            SourceOrderId sourceOrderId,
            OrderProductionViewStatus status,
            int itemCount,
            int inProductionCount,
            int partiallyReleasedCount,
            int releasedCount,
            int cancelledCount) {
        this.sourceOrderId = Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        this.status = Objects.requireNonNull(status, "status");
        if (itemCount < 0
                || inProductionCount < 0
                || partiallyReleasedCount < 0
                || releasedCount < 0
                || cancelledCount < 0) {
            throw new IllegalArgumentException("counts must be >= 0");
        }
        this.itemCount = itemCount;
        this.inProductionCount = inProductionCount;
        this.partiallyReleasedCount = partiallyReleasedCount;
        this.releasedCount = releasedCount;
        this.cancelledCount = cancelledCount;
    }

    public SourceOrderId sourceOrderId() {
        return sourceOrderId;
    }

    public OrderProductionViewStatus status() {
        return status;
    }

    public int itemCount() {
        return itemCount;
    }

    public int inProductionCount() {
        return inProductionCount;
    }

    public int partiallyReleasedCount() {
        return partiallyReleasedCount;
    }

    public int releasedCount() {
        return releasedCount;
    }

    public int cancelledCount() {
        return cancelledCount;
    }
}

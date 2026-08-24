package com.tmp.production.application;

import com.tmp.production.domain.CancellationItemAction;
import com.tmp.production.domain.ProductionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Result of whole-order Production Cancellation. */
public record CancelOrderProductionResult(
        UUID documentId,
        UUID sourceOrderId,
        Instant cancelledAt,
        List<ItemResult> itemResults) {

    public CancelOrderProductionResult {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        Objects.requireNonNull(cancelledAt, "cancelledAt");
        Objects.requireNonNull(itemResults, "itemResults");
        itemResults = List.copyOf(itemResults);
    }

    public record ItemResult(
            UUID sourceOrderItemId,
            UUID specificationId,
            ProductionStatus previousStatus,
            CancellationItemAction action,
            long activeQuantityCancelled,
            long releasedQuantityPreserved) {

        public ItemResult {
            Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
            Objects.requireNonNull(specificationId, "specificationId");
            Objects.requireNonNull(previousStatus, "previousStatus");
            Objects.requireNonNull(action, "action");
        }
    }
}

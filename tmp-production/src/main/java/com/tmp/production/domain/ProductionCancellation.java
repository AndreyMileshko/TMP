package com.tmp.production.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Production-owned whole-order Cancellation business document payload (Production Spec §16).
 *
 * <p>Identified by Document Engine {@code documentId}. Stores per-item cancellation evidence.
 * Posted cancellations are immutable.
 */
public final class ProductionCancellation {

    private final UUID documentId;
    private final SourceOrderId sourceOrderId;
    private final Instant cancelledAt;
    private final Optional<String> reason;
    private final List<ItemLine> itemLines;
    private final boolean posted;

    private ProductionCancellation(
            UUID documentId,
            SourceOrderId sourceOrderId,
            Instant cancelledAt,
            Optional<String> reason,
            List<ItemLine> itemLines,
            boolean posted) {
        this.documentId = Objects.requireNonNull(documentId, "documentId");
        this.sourceOrderId = Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        this.cancelledAt = Objects.requireNonNull(cancelledAt, "cancelledAt");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.itemLines = List.copyOf(Objects.requireNonNull(itemLines, "itemLines"));
        this.posted = posted;
        if (this.itemLines.isEmpty()) {
            throw new IllegalArgumentException(
                    "Production Cancellation requires at least one item line");
        }
        validateNoDuplicateItems(this.itemLines);
    }

    public static ProductionCancellation draft(
            UUID documentId,
            SourceOrderId sourceOrderId,
            Instant cancelledAt,
            Optional<String> reason,
            List<ItemLine> itemLines) {
        return new ProductionCancellation(
                documentId, sourceOrderId, cancelledAt, reason, itemLines, false);
    }

    public static ProductionCancellation restore(
            UUID documentId,
            SourceOrderId sourceOrderId,
            Instant cancelledAt,
            Optional<String> reason,
            List<ItemLine> itemLines,
            boolean posted) {
        return new ProductionCancellation(
                documentId, sourceOrderId, cancelledAt, reason, itemLines, posted);
    }

    public ProductionCancellation markPosted() {
        if (posted) {
            throw new ProductionCancellationImmutableException(documentId);
        }
        return new ProductionCancellation(
                documentId, sourceOrderId, cancelledAt, reason, itemLines, true);
    }

    public UUID documentId() {
        return documentId;
    }

    public SourceOrderId sourceOrderId() {
        return sourceOrderId;
    }

    public Instant cancelledAt() {
        return cancelledAt;
    }

    public Optional<String> reason() {
        return reason;
    }

    public List<ItemLine> itemLines() {
        return itemLines;
    }

    public boolean posted() {
        return posted;
    }

    private static void validateNoDuplicateItems(List<ItemLine> lines) {
        Set<SourceOrderItemId> seen = new HashSet<>();
        for (ItemLine line : lines) {
            if (!seen.add(line.sourceOrderItemId())) {
                throw new IllegalArgumentException(
                        "Duplicate sourceOrderItemId in Production Cancellation: "
                                + line.sourceOrderItemId().value());
            }
        }
    }

    /**
     * One item result line proving whole-order processing for the order.
     */
    public record ItemLine(
            SourceOrderItemId sourceOrderItemId,
            SpecificationId specificationId,
            ProductionStatus previousStatus,
            CancellationItemAction action,
            ProductionQuantity activeQuantityCancelled,
            ProductionQuantity releasedQuantityPreserved) {

        public ItemLine {
            Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
            Objects.requireNonNull(specificationId, "specificationId");
            Objects.requireNonNull(previousStatus, "previousStatus");
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(activeQuantityCancelled, "activeQuantityCancelled");
            Objects.requireNonNull(releasedQuantityPreserved, "releasedQuantityPreserved");
            if (action == CancellationItemAction.PRESERVED_RELEASED
                    && previousStatus != ProductionStatus.RELEASED) {
                throw new IllegalArgumentException(
                        "PRESERVED_RELEASED requires previousStatus RELEASED");
            }
            if (action == CancellationItemAction.CANCELLED_UNFINISHED
                    && previousStatus == ProductionStatus.RELEASED) {
                throw new IllegalArgumentException(
                        "CANCELLED_UNFINISHED cannot apply to RELEASED item");
            }
        }
    }
}

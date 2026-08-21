package com.tmp.production.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable production foundation frozen at Launch (Production Spec §5, §127).
 *
 * <p>Production stores the stable {@link SpecificationId} reference only — specification content
 * is resolved on demand via Order Management {@code getSpecificationById}. No revision semantics
 * and no content snapshot duplication.
 */
public final class ProductionFoundation {

    private final SourceOrderId sourceOrderId;
    private final SourceOrderItemId sourceOrderItemId;
    private final SpecificationId specificationId;
    private final Instant frozenAt;

    private ProductionFoundation(
            SourceOrderId sourceOrderId,
            SourceOrderItemId sourceOrderItemId,
            SpecificationId specificationId,
            Instant frozenAt) {
        this.sourceOrderId = Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        this.sourceOrderItemId = Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
        this.specificationId = Objects.requireNonNull(specificationId, "specificationId");
        this.frozenAt = Objects.requireNonNull(frozenAt, "frozenAt");
    }

    /**
     * Freezes the production foundation at Launch. The only permitted creation point.
     */
    public static ProductionFoundation freeze(
            SourceOrderId sourceOrderId,
            SourceOrderItemId sourceOrderItemId,
            SpecificationId specificationId,
            Instant frozenAt) {
        return new ProductionFoundation(sourceOrderId, sourceOrderItemId, specificationId, frozenAt);
    }

    /**
     * Reconstructs a persisted foundation. Used by persistence adapters only.
     */
    public static ProductionFoundation restore(
            SourceOrderId sourceOrderId,
            SourceOrderItemId sourceOrderItemId,
            SpecificationId specificationId,
            Instant frozenAt) {
        return new ProductionFoundation(sourceOrderId, sourceOrderItemId, specificationId, frozenAt);
    }

    public SourceOrderId sourceOrderId() {
        return sourceOrderId;
    }

    public SourceOrderItemId sourceOrderItemId() {
        return sourceOrderItemId;
    }

    public SpecificationId specificationId() {
        return specificationId;
    }

    public Instant frozenAt() {
        return frozenAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProductionFoundation that)) {
            return false;
        }
        return sourceOrderId.equals(that.sourceOrderId)
                && sourceOrderItemId.equals(that.sourceOrderItemId)
                && specificationId.equals(that.specificationId)
                && frozenAt.equals(that.frozenAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceOrderId, sourceOrderItemId, specificationId, frozenAt);
    }
}

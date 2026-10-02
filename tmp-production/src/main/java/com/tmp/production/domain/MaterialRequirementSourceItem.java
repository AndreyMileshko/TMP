package com.tmp.production.domain;

import java.util.Objects;

/**
 * One Production Order Item contribution of product units covered by a Material Requirement.
 *
 * <p>{@code requestedProductQuantity} is the number of products (not material quantity) included in
 * this requirement for the given order item.
 *
 * <p>{@code countsTowardProductCoverage} is {@code true} for Phase 2+ authoritative coverage.
 * Pre-V49 historical rows may be reconstructed for provenance/reopen with {@code false} so invented
 * quantities never enter cumulative submitted coverage.
 */
public final class MaterialRequirementSourceItem {

    private final SourceOrderId sourceOrderId;
    private final SourceOrderItemId sourceOrderItemId;
    private final long requestedProductQuantity;
    private final boolean countsTowardProductCoverage;

    private MaterialRequirementSourceItem(
            SourceOrderId sourceOrderId,
            SourceOrderItemId sourceOrderItemId,
            long requestedProductQuantity,
            boolean countsTowardProductCoverage) {
        this.sourceOrderId = Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        this.sourceOrderItemId = Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
        if (requestedProductQuantity <= 0L) {
            throw new IllegalArgumentException(
                    "requestedProductQuantity must be > 0: " + requestedProductQuantity);
        }
        this.requestedProductQuantity = requestedProductQuantity;
        this.countsTowardProductCoverage = countsTowardProductCoverage;
    }

    /** Authoritative Phase 2+ source item (counts toward submitted product coverage). */
    public static MaterialRequirementSourceItem of(
            SourceOrderId sourceOrderId,
            SourceOrderItemId sourceOrderItemId,
            long requestedProductQuantity) {
        return new MaterialRequirementSourceItem(
                sourceOrderId, sourceOrderItemId, requestedProductQuantity, true);
    }

    /** Persistence / historical reconstruction entry point. */
    public static MaterialRequirementSourceItem rehydrate(
            SourceOrderId sourceOrderId,
            SourceOrderItemId sourceOrderItemId,
            long requestedProductQuantity,
            boolean countsTowardProductCoverage) {
        return new MaterialRequirementSourceItem(
                sourceOrderId,
                sourceOrderItemId,
                requestedProductQuantity,
                countsTowardProductCoverage);
    }

    public SourceOrderId sourceOrderId() {
        return sourceOrderId;
    }

    public SourceOrderItemId sourceOrderItemId() {
        return sourceOrderItemId;
    }

    public long requestedProductQuantity() {
        return requestedProductQuantity;
    }

    public boolean countsTowardProductCoverage() {
        return countsTowardProductCoverage;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MaterialRequirementSourceItem that)) {
            return false;
        }
        return requestedProductQuantity == that.requestedProductQuantity
                && countsTowardProductCoverage == that.countsTowardProductCoverage
                && sourceOrderId.equals(that.sourceOrderId)
                && sourceOrderItemId.equals(that.sourceOrderItemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                sourceOrderId,
                sourceOrderItemId,
                requestedProductQuantity,
                countsTowardProductCoverage);
    }

    @Override
    public String toString() {
        return "MaterialRequirementSourceItem{"
                + sourceOrderId
                + "/"
                + sourceOrderItemId
                + " x"
                + requestedProductQuantity
                + ", coverage="
                + countsTowardProductCoverage
                + "}";
    }
}

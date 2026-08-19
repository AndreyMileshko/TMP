package com.tmp.production.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Item-owned production state (Production Spec §5).
 *
 * <p>Identified by {@code SourceOrderId + SourceOrderItemId + SpecificationId}. Created at Launch
 * with a non-null specification reference. Absence of state before Launch is interpreted as
 * {@link ProductionStatus#NOT_STARTED} at application/query boundaries only.
 */
public final class ProductionItemState {

    private final SourceOrderId sourceOrderId;
    private final SourceOrderItemId sourceOrderItemId;
    private final SpecificationId specificationId;
    private final ProductionStatus status;
    private final ProductionQuantity orderedQuantity;
    private final ProductionQuantity launchedQuantity;
    private final ProductionQuantity activeProductionQuantity;
    private final ProductionQuantity releasedQuantity;
    private final Instant lastMaterialCheckAt;
    private final Instant lastStatusChangedAt;

    private ProductionItemState(
            SourceOrderId sourceOrderId,
            SourceOrderItemId sourceOrderItemId,
            SpecificationId specificationId,
            ProductionStatus status,
            ProductionQuantity orderedQuantity,
            ProductionQuantity launchedQuantity,
            ProductionQuantity activeProductionQuantity,
            ProductionQuantity releasedQuantity,
            Instant lastMaterialCheckAt,
            Instant lastStatusChangedAt) {
        this.sourceOrderId = Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        this.sourceOrderItemId = Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
        this.specificationId = Objects.requireNonNull(specificationId, "specificationId");
        this.status = Objects.requireNonNull(status, "status");
        this.orderedQuantity = Objects.requireNonNull(orderedQuantity, "orderedQuantity");
        this.launchedQuantity = Objects.requireNonNull(launchedQuantity, "launchedQuantity");
        this.activeProductionQuantity =
                Objects.requireNonNull(activeProductionQuantity, "activeProductionQuantity");
        this.releasedQuantity = Objects.requireNonNull(releasedQuantity, "releasedQuantity");
        this.lastMaterialCheckAt = lastMaterialCheckAt;
        this.lastStatusChangedAt =
                Objects.requireNonNull(lastStatusChangedAt, "lastStatusChangedAt");
        if (status == ProductionStatus.NOT_STARTED) {
            throw new IllegalArgumentException(
                    "Persisted production state cannot use NOT_STARTED; absence of state means not started");
        }
        validateQuantities();
    }

    /**
     * Creates production state when an order item is accepted into production at Launch.
     *
     * <p>Whole-order Launch accepts the full ordered quantity for the item.
     */
    public static ProductionItemState launch(
            SourceOrderId sourceOrderId,
            SourceOrderItemId sourceOrderItemId,
            SpecificationId specificationId,
            ProductionQuantity orderedQuantity,
            Instant launchedAt) {
        Objects.requireNonNull(launchedAt, "launchedAt");
        return new ProductionItemState(
                sourceOrderId,
                sourceOrderItemId,
                specificationId,
                ProductionStatus.IN_PRODUCTION,
                orderedQuantity,
                orderedQuantity,
                orderedQuantity,
                ProductionQuantity.zero(),
                null,
                launchedAt);
    }

    /**
     * Records a product release against active production quantity.
     */
    public ProductionItemState release(ProductionQuantity releaseAmount, Instant releasedAt) {
        Objects.requireNonNull(releaseAmount, "releaseAmount");
        Objects.requireNonNull(releasedAt, "releasedAt");
        if (status != ProductionStatus.IN_PRODUCTION && status != ProductionStatus.PARTIALLY_RELEASED) {
            throw new InvalidProductionStateException(
                    "Release is allowed only from IN_PRODUCTION or PARTIALLY_RELEASED, current: " + status);
        }
        if (releaseAmount.isZero()) {
            throw new IllegalArgumentException("Release amount must be > 0");
        }
        if (!releaseAmount.isLessThanOrEqualTo(activeProductionQuantity)) {
            throw new InvalidProductionStateException(
                    "Release amount exceeds active production quantity: "
                            + releaseAmount
                            + " > "
                            + activeProductionQuantity);
        }
        ProductionQuantity newReleased = releasedQuantity.plus(releaseAmount);
        ProductionQuantity newActive = activeProductionQuantity.minus(releaseAmount);
        ProductionStatus newStatus =
                newReleased.equals(orderedQuantity)
                        ? ProductionStatus.RELEASED
                        : ProductionStatus.PARTIALLY_RELEASED;
        return new ProductionItemState(
                sourceOrderId,
                sourceOrderItemId,
                specificationId,
                newStatus,
                orderedQuantity,
                launchedQuantity,
                newActive,
                newReleased,
                lastMaterialCheckAt,
                releasedAt);
    }

    /**
     * Cancels unfinished production for the item. Already released quantity is preserved.
     */
    public ProductionItemState cancel(Instant cancelledAt) {
        Objects.requireNonNull(cancelledAt, "cancelledAt");
        if (status == ProductionStatus.RELEASED) {
            throw new InvalidProductionStateException("Released production cannot be cancelled");
        }
        if (status == ProductionStatus.CANCELLED) {
            throw new InvalidProductionStateException("Production is already cancelled");
        }
        return new ProductionItemState(
                sourceOrderId,
                sourceOrderItemId,
                specificationId,
                ProductionStatus.CANCELLED,
                orderedQuantity,
                launchedQuantity,
                ProductionQuantity.zero(),
                releasedQuantity,
                lastMaterialCheckAt,
                cancelledAt);
    }

    /**
     * Reconstructs persisted item-owned state from storage. Used by persistence adapters only;
     * domain invariants are enforced by the private constructor.
     */
    public static ProductionItemState rehydrate(
            SourceOrderId sourceOrderId,
            SourceOrderItemId sourceOrderItemId,
            SpecificationId specificationId,
            ProductionStatus status,
            ProductionQuantity orderedQuantity,
            ProductionQuantity launchedQuantity,
            ProductionQuantity activeProductionQuantity,
            ProductionQuantity releasedQuantity,
            Instant lastMaterialCheckAt,
            Instant lastStatusChangedAt) {
        return new ProductionItemState(
                sourceOrderId,
                sourceOrderItemId,
                specificationId,
                status,
                orderedQuantity,
                launchedQuantity,
                activeProductionQuantity,
                releasedQuantity,
                lastMaterialCheckAt,
                lastStatusChangedAt);
    }

    /**
     * Records the last material availability check timestamp without changing production status.
     */
    public ProductionItemState recordMaterialCheck(Instant checkedAt) {
        Objects.requireNonNull(checkedAt, "checkedAt");
        if (status == ProductionStatus.CANCELLED) {
            throw new InvalidProductionStateException(
                    "Material check cannot be recorded for cancelled production");
        }
        return new ProductionItemState(
                sourceOrderId,
                sourceOrderItemId,
                specificationId,
                status,
                orderedQuantity,
                launchedQuantity,
                activeProductionQuantity,
                releasedQuantity,
                checkedAt,
                lastStatusChangedAt);
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

    public ProductionStatus status() {
        return status;
    }

    public ProductionQuantity orderedQuantity() {
        return orderedQuantity;
    }

    public ProductionQuantity launchedQuantity() {
        return launchedQuantity;
    }

    public ProductionQuantity activeProductionQuantity() {
        return activeProductionQuantity;
    }

    public ProductionQuantity releasedQuantity() {
        return releasedQuantity;
    }

    public Instant lastMaterialCheckAt() {
        return lastMaterialCheckAt;
    }

    public Instant lastStatusChangedAt() {
        return lastStatusChangedAt;
    }

    private void validateQuantities() {
        if (!launchedQuantity.isLessThanOrEqualTo(orderedQuantity)) {
            throw new IllegalArgumentException(
                    "Launched quantity must not exceed ordered quantity: "
                            + launchedQuantity
                            + " > "
                            + orderedQuantity);
        }
        if (!releasedQuantity.isLessThanOrEqualTo(launchedQuantity)) {
            throw new IllegalArgumentException(
                    "Released quantity must not exceed launched quantity: "
                            + releasedQuantity
                            + " > "
                            + launchedQuantity);
        }
        if (!releasedQuantity.isLessThanOrEqualTo(orderedQuantity)) {
            throw new IllegalArgumentException(
                    "Released quantity must not exceed ordered quantity: "
                            + releasedQuantity
                            + " > "
                            + orderedQuantity);
        }
        if (status != ProductionStatus.CANCELLED
                && !activeProductionQuantity.plus(releasedQuantity).equals(launchedQuantity)) {
            throw new IllegalArgumentException(
                    "Active plus released must equal launched quantity: "
                            + activeProductionQuantity
                            + " + "
                            + releasedQuantity
                            + " != "
                            + launchedQuantity);
        }
    }
}

package com.tmp.production.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Production-owned Production Release business document payload (Production Spec §15).
 *
 * <p>Identified by Document Engine {@code documentId}. Stores item release quantities and
 * material plan/fact history. Posted releases are immutable.
 */
public final class ProductionRelease {

    private final UUID documentId;
    private final SourceOrderId sourceOrderId;
    private final Instant releasedAt;
    private final List<ItemLine> itemLines;
    private final List<MaterialLine> materialLines;
    private final boolean posted;

    private ProductionRelease(
            UUID documentId,
            SourceOrderId sourceOrderId,
            Instant releasedAt,
            List<ItemLine> itemLines,
            List<MaterialLine> materialLines,
            boolean posted) {
        this.documentId = Objects.requireNonNull(documentId, "documentId");
        this.sourceOrderId = Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        this.releasedAt = Objects.requireNonNull(releasedAt, "releasedAt");
        this.itemLines = List.copyOf(Objects.requireNonNull(itemLines, "itemLines"));
        this.materialLines = List.copyOf(Objects.requireNonNull(materialLines, "materialLines"));
        this.posted = posted;
        if (this.itemLines.isEmpty()) {
            throw new IllegalArgumentException("Production Release requires at least one item line");
        }
        validateNoDuplicateItems(this.itemLines);
    }

    /**
     * Creates a DRAFT release payload bound to a Document Engine document.
     */
    public static ProductionRelease draft(
            UUID documentId,
            SourceOrderId sourceOrderId,
            Instant releasedAt,
            List<ItemLine> itemLines,
            List<MaterialLine> materialLines) {
        return new ProductionRelease(
                documentId, sourceOrderId, releasedAt, itemLines, materialLines, false);
    }

    /**
     * Rehydrates a persisted release including posted flag.
     */
    public static ProductionRelease restore(
            UUID documentId,
            SourceOrderId sourceOrderId,
            Instant releasedAt,
            List<ItemLine> itemLines,
            List<MaterialLine> materialLines,
            boolean posted) {
        return new ProductionRelease(
                documentId, sourceOrderId, releasedAt, itemLines, materialLines, posted);
    }

    /**
     * Returns an immutable posted copy. Allowed only while draft.
     */
    public ProductionRelease markPosted() {
        if (posted) {
            throw new ProductionReleaseImmutableException(documentId);
        }
        return new ProductionRelease(
                documentId, sourceOrderId, releasedAt, itemLines, materialLines, true);
    }

    /**
     * Replaces draft content. Rejected after POST.
     */
    public ProductionRelease replaceDraftContent(
            Instant newReleasedAt, List<ItemLine> newItemLines, List<MaterialLine> newMaterialLines) {
        if (posted) {
            throw new ProductionReleaseImmutableException(documentId);
        }
        return new ProductionRelease(
                documentId, sourceOrderId, newReleasedAt, newItemLines, newMaterialLines, false);
    }

    /**
     * Actual material usage for future Warehouse Consumption aggregation (STAGE7-013).
     *
     * <p>Does not allocate cells or call Warehouse.
     */
    public List<ActualMaterialUsage> actualMaterialUsages() {
        return materialLines.stream()
                .map(
                        line ->
                                new ActualMaterialUsage(
                                        line.materialReferenceId(),
                                        line.actualQuantity(),
                                        line.sourceOrderItemId(),
                                        line.cuttingPlanId()))
                .toList();
    }

    public UUID documentId() {
        return documentId;
    }

    public SourceOrderId sourceOrderId() {
        return sourceOrderId;
    }

    public Instant releasedAt() {
        return releasedAt;
    }

    public List<ItemLine> itemLines() {
        return itemLines;
    }

    public List<MaterialLine> materialLines() {
        return materialLines;
    }

    public boolean posted() {
        return posted;
    }

    private static void validateNoDuplicateItems(List<ItemLine> lines) {
        Set<SourceOrderItemId> seen = new HashSet<>();
        for (ItemLine line : lines) {
            if (!seen.add(line.sourceOrderItemId())) {
                throw new IllegalArgumentException(
                        "Duplicate sourceOrderItemId in Production Release: "
                                + line.sourceOrderItemId().value());
            }
        }
    }

    /**
     * One Order Item release line. Release quantity must be &gt; 0 (whole number).
     */
    public record ItemLine(
            SourceOrderItemId sourceOrderItemId,
            SpecificationId specificationId,
            ProductionQuantity releaseQuantity) {

        public ItemLine {
            Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
            Objects.requireNonNull(specificationId, "specificationId");
            Objects.requireNonNull(releaseQuantity, "releaseQuantity");
            if (releaseQuantity.isZero()) {
                throw new IllegalArgumentException("Release quantity must be > 0");
            }
        }
    }

    /**
     * Material plan/fact line. Planned and actual quantities are independently non-negative;
     * actual may be less than, equal to, or greater than planned.
     */
    public record MaterialLine(
            MaterialReferenceId materialReferenceId,
            BigDecimal plannedQuantity,
            BigDecimal actualQuantity,
            MaterialPlanningSource planningSource,
            Optional<CuttingPlanId> cuttingPlanId,
            Optional<SourceOrderItemId> sourceOrderItemId,
            Optional<String> comment) {

        public MaterialLine {
            Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            Objects.requireNonNull(plannedQuantity, "plannedQuantity");
            Objects.requireNonNull(actualQuantity, "actualQuantity");
            Objects.requireNonNull(planningSource, "planningSource");
            Objects.requireNonNull(cuttingPlanId, "cuttingPlanId");
            Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
            Objects.requireNonNull(comment, "comment");
            plannedQuantity = requireNonNegative(plannedQuantity, "plannedQuantity");
            actualQuantity = requireNonNegative(actualQuantity, "actualQuantity");
            comment =
                    comment.map(String::trim).filter(value -> !value.isEmpty());
        }

        /**
         * {@code actualQuantity - plannedQuantity}. Positive means over-consumption vs plan.
         */
        public BigDecimal actualMinusPlanned() {
            return actualQuantity.subtract(plannedQuantity);
        }

        private static BigDecimal requireNonNegative(BigDecimal value, String name) {
            if (value.signum() < 0) {
                throw new IllegalArgumentException(name + " must be >= 0: " + value);
            }
            return value;
        }
    }

    /**
     * Read-only actual material usage projection for STAGE7-013 Warehouse Consumption.
     */
    public record ActualMaterialUsage(
            MaterialReferenceId materialReferenceId,
            BigDecimal actualQuantity,
            Optional<SourceOrderItemId> sourceOrderItemId,
            Optional<CuttingPlanId> cuttingPlanId) {

        public ActualMaterialUsage {
            Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            Objects.requireNonNull(actualQuantity, "actualQuantity");
            Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
            Objects.requireNonNull(cuttingPlanId, "cuttingPlanId");
        }
    }
}

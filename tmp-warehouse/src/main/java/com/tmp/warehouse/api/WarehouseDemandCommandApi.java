package com.tmp.warehouse.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Warehouse-owned inter-capability Demand command contract (ADR-037 / ADR-038 / B3B-2).
 *
 * <p>Trusted backend orchestration boundary: Production submits an immutable Material Requirement
 * snapshot; Warehouse persists a Demand always (for correctly described materials), resolves
 * MaterialReferences operationally, and best-effort routes uniquely resolved lines to Transfer
 * DRAFTs. Demand ≠ Transfer. Business no-route outcomes (unmatched / ambiguous / zero stock) are
 * successful acceptance with WAITING reasons — not exceptions.
 *
 * <p>This API is intentionally NOT part of {@link WarehouseCommandApi} and is NOT a user-facing
 * Warehouse operation. It must not be invoked from UI. It does NOT check {@link
 * com.tmp.warehouse.application.WarehouseResponsibilityGuard} and does NOT require {@code
 * warehouse.transfer}: the calling capability owns its own authorization.
 *
 * <p>Idempotency is owned by Warehouse via {@code UNIQUE(sourceMaterialRequirementId)}. Identical
 * repeat accepts return the existing Demand. Semantic payload mismatch raises {@link
 * WarehouseDemandPayloadConflictException}.
 */
public interface WarehouseDemandCommandApi {

    /**
     * Accepts a Production Material Requirement as a Warehouse Demand, resolves materials, and
     * best-effort creates Transfer DRAFTs for uniquely resolved lines with positive AVAILABLE
     * source stock. Must be called inside the caller's transaction; participates in it
     * (PROPAGATION_REQUIRED).
     *
     * @throws WarehouseDemandPayloadConflictException if the same source MR id is resent with a
     *     different immutable acceptance payload
     * @throws IllegalArgumentException if the destination is missing or input is invalid
     * @throws com.tmp.warehouse.domain.InvalidWarehouseStateException if the destination is inactive
     */
    AcceptProductionDemandResult acceptProductionDemand(AcceptProductionDemandCommand command);

    /**
     * Manually retries routing for all Demand lines currently derived as {@code
     * WAITING_FOR_SUPPLY}. Re-resolves MaterialReference from the immutable snapshot and routes
     * only the remaining quantity ({@code max(0, required − received)}). Does not create a new
     * Demand. Business no-route outcomes succeed without exception. Lines that are fulfilled, in
     * fulfillment (active Transfer), or cancelled are skipped. Must not be invoked from UI in
     * this phase.
     *
     * @throws IllegalArgumentException if the Demand does not exist
     */
    RetryDemandRoutingResult retryDemandRouting(UUID demandId);

    /** Production → Warehouse acceptance command. */
    record AcceptProductionDemandCommand(
            UUID sourceMaterialRequirementId,
            UUID destinationWarehouseId,
            String acceptedBy,
            List<ProductionDemandLine> lines) {
        public AcceptProductionDemandCommand {
            Objects.requireNonNull(sourceMaterialRequirementId, "sourceMaterialRequirementId");
            Objects.requireNonNull(destinationWarehouseId, "destinationWarehouseId");
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }

    /**
     * One immutable Production MR line snapshot. {@code materialReferenceIdHint} is optional and
     * never trusted blindly — Warehouse re-resolves from snapshot identity.
     */
    record ProductionDemandLine(
            UUID sourceMaterialRequirementLineId,
            String materialCode,
            String materialName,
            String color,
            String unitOfMeasure,
            BigDecimal lengthMm,
            BigDecimal quantity,
            UUID materialReferenceIdHint) {
        public ProductionDemandLine {
            Objects.requireNonNull(
                    sourceMaterialRequirementLineId, "sourceMaterialRequirementLineId");
            Objects.requireNonNull(materialCode, "materialCode");
            if (materialCode.isBlank()) {
                throw new IllegalArgumentException("materialCode must not be blank");
            }
            Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
            if (unitOfMeasure.isBlank()) {
                throw new IllegalArgumentException("unitOfMeasure must not be blank");
            }
            Objects.requireNonNull(quantity, "quantity");
            if (quantity.signum() <= 0) {
                throw new IllegalArgumentException("quantity must be positive: " + quantity);
            }
            if (lengthMm != null && lengthMm.signum() <= 0) {
                throw new IllegalArgumentException("lengthMm must be > 0 when present: " + lengthMm);
            }
        }
    }

    record AcceptProductionDemandResult(
            UUID demandId,
            boolean created,
            List<DemandLineOutcome> lineOutcomes,
            List<GeneratedDocument> documents) {
        public AcceptProductionDemandResult {
            Objects.requireNonNull(demandId, "demandId");
            lineOutcomes = lineOutcomes == null ? List.of() : List.copyOf(lineOutcomes);
            documents = documents == null ? List.of() : List.copyOf(documents);
        }
    }

    enum DemandLineRoutingOutcome {
        ROUTED,
        MATERIAL_UNMATCHED,
        MATERIAL_AMBIGUOUS,
        NO_AVAILABLE_STOCK
    }

    record DemandLineOutcome(
            UUID sourceMaterialRequirementLineId,
            UUID demandLineId,
            UUID materialReferenceId,
            DemandLineRoutingOutcome outcome,
            UUID warehouseDocumentId,
            UUID warehouseTransferLineId,
            BigDecimal linkedQuantity) {
        public DemandLineOutcome {
            Objects.requireNonNull(
                    sourceMaterialRequirementLineId, "sourceMaterialRequirementLineId");
            Objects.requireNonNull(demandLineId, "demandLineId");
            Objects.requireNonNull(outcome, "outcome");
        }
    }

    /** One generated DRAFT Transfer Document reference (no Warehouse internals exposed). */
    record GeneratedDocument(
            UUID documentId, UUID sourceWarehouseId, UUID destinationWarehouseId) {
        public GeneratedDocument {
            Objects.requireNonNull(documentId, "documentId");
            Objects.requireNonNull(sourceWarehouseId, "sourceWarehouseId");
            Objects.requireNonNull(destinationWarehouseId, "destinationWarehouseId");
        }
    }

    /** Per-line business outcome of {@link #retryDemandRouting(UUID)}. */
    enum RetryDemandLineOutcome {
        ROUTED,
        STILL_UNMATCHED,
        STILL_AMBIGUOUS,
        NO_AVAILABLE_STOCK,
        SKIPPED_IN_FULFILLMENT,
        SKIPPED_FULFILLED,
        SKIPPED_CANCELLED
    }

    record RetryDemandLineResult(
            UUID demandLineId,
            UUID sourceMaterialRequirementLineId,
            RetryDemandLineOutcome outcome,
            UUID materialReferenceId,
            BigDecimal routedQuantity,
            UUID warehouseDocumentId,
            UUID warehouseTransferLineId) {
        public RetryDemandLineResult {
            Objects.requireNonNull(demandLineId, "demandLineId");
            Objects.requireNonNull(
                    sourceMaterialRequirementLineId, "sourceMaterialRequirementLineId");
            Objects.requireNonNull(outcome, "outcome");
        }
    }

    /**
     * Compact typed result of manual Demand routing retry. Empty {@code documents} means nothing
     * was routed (already fulfilled / cancelled / still waiting / active fulfillment).
     */
    record RetryDemandRoutingResult(
            UUID demandId,
            List<RetryDemandLineResult> lineOutcomes,
            List<GeneratedDocument> documents) {
        public RetryDemandRoutingResult {
            Objects.requireNonNull(demandId, "demandId");
            lineOutcomes = lineOutcomes == null ? List.of() : List.copyOf(lineOutcomes);
            documents = documents == null ? List.of() : List.copyOf(documents);
        }
    }
}

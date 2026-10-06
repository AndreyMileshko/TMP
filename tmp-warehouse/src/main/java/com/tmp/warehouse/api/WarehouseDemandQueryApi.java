package com.tmp.warehouse.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Warehouse-owned Demand query contract (ADR-038 / B3B-3A).
 *
 * <p>Read-only immutable views. Received quantities and statuses are derived from settled Transfer
 * receipt facts and linked Transfer lifecycle — not from mutable Demand counters. Does not expose
 * Warehouse persistence entities. Must not depend on Production packages.
 */
public interface WarehouseDemandQueryApi {

    Optional<WarehouseDemandView> getDemand(UUID demandId);

    Optional<WarehouseDemandView> getDemandBySourceMaterialRequirementId(
            UUID sourceMaterialRequirementId);

    /**
     * Demand-backed supply-task detail projection (B3B-3C1): waiting lines only when the Demand
     * currently qualifies for {@code PRODUCTION_MATERIAL_SUPPLY}. Empty when cancelled, fulfilled,
     * or every open obligation is already covered by an active Transfer.
     */
    Optional<WarehouseDemandSupplyTaskView> getDemandSupplyTask(UUID demandId);

    /** Immutable Demand header + derived status + lines. */
    record WarehouseDemandView(
            UUID demandId,
            UUID sourceMaterialRequirementId,
            UUID destinationWarehouseId,
            Instant acceptedAt,
            String acceptedBy,
            Instant cancelledAt,
            String cancelledBy,
            WarehouseDemandDerivedStatus derivedStatus,
            List<WarehouseDemandLineView> lines) {

        public WarehouseDemandView {
            Objects.requireNonNull(demandId, "demandId");
            Objects.requireNonNull(sourceMaterialRequirementId, "sourceMaterialRequirementId");
            Objects.requireNonNull(destinationWarehouseId, "destinationWarehouseId");
            Objects.requireNonNull(acceptedAt, "acceptedAt");
            Objects.requireNonNull(derivedStatus, "derivedStatus");
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }

    /** Immutable Demand line with derived fulfillment quantities and status. */
    record WarehouseDemandLineView(
            UUID demandLineId,
            UUID sourceMaterialRequirementLineId,
            String materialCode,
            String materialName,
            String color,
            String unitOfMeasure,
            BigDecimal lengthMm,
            BigDecimal requiredQuantity,
            BigDecimal receivedQuantity,
            BigDecimal remainingQuantity,
            UUID materialReferenceId,
            WarehouseDemandDerivedStatus derivedStatus,
            String effectiveWaitingReason,
            List<LinkedTransferRef> linkedTransfers) {

        public WarehouseDemandLineView {
            Objects.requireNonNull(demandLineId, "demandLineId");
            Objects.requireNonNull(
                    sourceMaterialRequirementLineId, "sourceMaterialRequirementLineId");
            Objects.requireNonNull(materialCode, "materialCode");
            Objects.requireNonNull(color, "color");
            Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
            Objects.requireNonNull(requiredQuantity, "requiredQuantity");
            Objects.requireNonNull(receivedQuantity, "receivedQuantity");
            Objects.requireNonNull(remainingQuantity, "remainingQuantity");
            Objects.requireNonNull(derivedStatus, "derivedStatus");
            linkedTransfers =
                    linkedTransfers == null ? List.of() : List.copyOf(linkedTransfers);
        }
    }

    /** Explicit Demand ↔ Transfer line lineage (traceability only). */
    record LinkedTransferRef(
            UUID transferDocumentId, UUID transferLineId, BigDecimal linkedQuantity) {

        public LinkedTransferRef {
            Objects.requireNonNull(transferDocumentId, "transferDocumentId");
            Objects.requireNonNull(transferLineId, "transferLineId");
            Objects.requireNonNull(linkedQuantity, "linkedQuantity");
        }
    }

    /**
     * Compact supply-task projection: Demand header + WAITING lines only (B3B-3C1). Does not expose
     * Demand/MR UUIDs in UI contracts beyond opaque ids required for take-in-work identity.
     */
    record WarehouseDemandSupplyTaskView(
            UUID demandId,
            UUID destinationWarehouseId,
            Instant acceptedAt,
            List<WarehouseDemandSupplyWaitingLineView> waitingLines) {

        public WarehouseDemandSupplyTaskView {
            Objects.requireNonNull(demandId, "demandId");
            Objects.requireNonNull(destinationWarehouseId, "destinationWarehouseId");
            Objects.requireNonNull(acceptedAt, "acceptedAt");
            waitingLines = waitingLines == null ? List.of() : List.copyOf(waitingLines);
            if (waitingLines.isEmpty()) {
                throw new IllegalArgumentException("waitingLines must not be empty");
            }
        }
    }

    /** Waiting-line facts for future supply-task detail UI (C2/C3). */
    record WarehouseDemandSupplyWaitingLineView(
            UUID demandLineId,
            String materialCode,
            String materialName,
            String color,
            String unitOfMeasure,
            BigDecimal lengthMm,
            BigDecimal requiredQuantity,
            BigDecimal receivedQuantity,
            BigDecimal remainingQuantity,
            String effectiveWaitingReason) {

        public WarehouseDemandSupplyWaitingLineView {
            Objects.requireNonNull(demandLineId, "demandLineId");
            Objects.requireNonNull(materialCode, "materialCode");
            Objects.requireNonNull(color, "color");
            Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
            Objects.requireNonNull(requiredQuantity, "requiredQuantity");
            Objects.requireNonNull(receivedQuantity, "receivedQuantity");
            Objects.requireNonNull(remainingQuantity, "remainingQuantity");
            Objects.requireNonNull(effectiveWaitingReason, "effectiveWaitingReason");
        }
    }
}

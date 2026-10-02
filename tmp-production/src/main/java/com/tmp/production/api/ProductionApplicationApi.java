package com.tmp.production.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production UI-facing Application API (Production Spec §18.2).
 *
 * <p>Mutating use-case boundary for the Production workbench. Returns only UUID/DTO types — never
 * domain or persistence types. Not a Public mutating API for other Capabilities.
 */
public interface ProductionApplicationApi {

    /**
     * Returns the Warehouse-managed Production destination warehouse id when assigned. Empty means
     * none assigned (valid configuration state — no hidden MAIN/SECOND fallback).
     */
    DestinationWarehouseView destinationWarehouse();

    void acceptOrderIntoProduction(UUID orderId, String createdBy);

    /**
     * Runs the material availability check (persists MATERIALS_CHECKED history). Caller refreshes
     * the latest snapshot via {@link ProductionQueryApi#getMaterialAvailabilityResult}.
     */
    void checkMaterialAvailability(UUID orderId);

    /**
     * Creates a DRAFT Material Requirement from cross-order product selections (Stage 7 Phase 2).
     * Does not create Warehouse transfers or mutate stock.
     */
    MaterialRequirementView prepareMaterialRequirement(
            List<MaterialRequirementProductSelectionView> selections);

    /**
     * Loads a persisted Material Requirement by id (reopen / DEP-1). Requires
     * {@code production.order.view}.
     */
    Optional<MaterialRequirementView> getMaterialRequirement(UUID requirementId);

    /**
     * Batch product-coverage facts for the given Order Items. Requires
     * {@code production.order.view}.
     */
    List<MaterialRequirementProductCoverageView> getMaterialRequirementProductCoverage(
            List<MaterialRequirementSourceItemRefView> sourceItems);

    /**
     * Edits one Material Requirement line quantity with optimistic concurrency (Stage 3.5.9).
     */
    MaterialRequirementView changeMaterialRequirementQuantity(
            UUID requirementId, UUID lineId, BigDecimal quantity, long expectedVersion);

    /**
     * Submits a DRAFT Material Requirement to the Warehouse (Stage 3.5.10): automatic source
     * routing + grouped DRAFT Transfer Documents in one ACID transaction. Idempotent for an
     * already-SUBMITTED requirement. Does not move physical stock.
     */
    SubmitMaterialRequirementResultView submitMaterialRequirement(
            UUID requirementId, long expectedVersion);

    List<LogicalTransferView> listLogicalTransfers(UUID orderId);

    ReceiptResultView confirmMaterialReceipt(UUID logicalTransferId);

    ReleasePreviewView prepareRelease(UUID orderId, List<ItemReleaseView> itemReleases);

    ReleaseResultView releaseProducts(
            UUID orderId,
            List<ItemReleaseView> itemReleases,
            List<MaterialActualUsageView> materialActualUsages);

    void cancelOrderProduction(UUID orderId, Optional<String> reason);

    /**
     * Returns the order's Production quantity mode; {@code STANDARD} with {@code version 0} when
     * the mode was never changed. Requires {@code production.order.view}.
     */
    OrderQuantityModeView getOrderQuantityMode(UUID orderId);

    /**
     * Changes the order's Production quantity mode with optimistic concurrency and returns the
     * persisted state. Allowed in every Production state; never alters past releases or material
     * requirements. Requires {@code production.order.accept}.
     */
    OrderQuantityModeView changeOrderQuantityMode(
            UUID orderId, QuantityModeView quantityMode, long expectedVersion);

    enum QuantityModeView {
        STANDARD,
        FLEXIBLE
    }

    record OrderQuantityModeView(UUID orderId, QuantityModeView quantityMode, long version) {
        public OrderQuantityModeView {
            Objects.requireNonNull(orderId, "orderId");
            Objects.requireNonNull(quantityMode, "quantityMode");
        }
    }

    record DestinationWarehouseView(Optional<UUID> productionWarehouseId) {
        public DestinationWarehouseView {
            productionWarehouseId =
                    productionWarehouseId == null ? Optional.empty() : productionWarehouseId;
        }
    }

    enum MaterialRequirementStatusView {
        DRAFT,
        SUBMITTED
    }

    enum MaterialPlanningSourceView {
        SPECIFICATION,
        CUTTING_PLAN
    }

    enum CuttingLinkStatusView {
        NONE,
        SINGLE,
        MULTIPLE_REFERENCES
    }

    record MaterialRequirementSourceItemView(
            UUID sourceOrderId, UUID sourceOrderItemId, long requestedProductQuantity) {
        public MaterialRequirementSourceItemView {
            Objects.requireNonNull(sourceOrderId, "sourceOrderId");
            Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
            if (requestedProductQuantity <= 0L) {
                throw new IllegalArgumentException(
                        "requestedProductQuantity must be > 0: " + requestedProductQuantity);
            }
        }
    }

    record MaterialRequirementSourceItemRefView(UUID sourceOrderId, UUID sourceOrderItemId) {
        public MaterialRequirementSourceItemRefView {
            Objects.requireNonNull(sourceOrderId, "sourceOrderId");
            Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
        }
    }

    /**
     * Product selection for prepare. {@code requestedProductQuantity} empty means STANDARD
     * (backend computes). Present value is required for FLEXIBLE.
     */
    record MaterialRequirementProductSelectionView(
            UUID sourceOrderId,
            UUID sourceOrderItemId,
            Optional<Long> requestedProductQuantity) {
        public MaterialRequirementProductSelectionView {
            Objects.requireNonNull(sourceOrderId, "sourceOrderId");
            Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
            requestedProductQuantity =
                    requestedProductQuantity == null ? Optional.empty() : requestedProductQuantity;
        }
    }

    record MaterialRequirementProductCoverageView(
            UUID sourceOrderId,
            UUID sourceOrderItemId,
            long orderedQuantity,
            long activeProductionQuantity,
            long releasedQuantity,
            long submittedProductCoverage,
            long outstandingSubmittedCoverage,
            long requestableProductQuantity) {
        public MaterialRequirementProductCoverageView {
            Objects.requireNonNull(sourceOrderId, "sourceOrderId");
            Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
        }
    }

    record MaterialRequirementLineView(
            UUID lineId,
            UUID materialReferenceId,
            String materialCode,
            String materialName,
            String color,
            String unitOfMeasure,
            BigDecimal quantity,
            List<UUID> sourceOrderItemIds) {
        public MaterialRequirementLineView {
            Objects.requireNonNull(lineId, "lineId");
            Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            Objects.requireNonNull(materialCode, "materialCode");
            Objects.requireNonNull(materialName, "materialName");
            Objects.requireNonNull(color, "color");
            Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
            Objects.requireNonNull(quantity, "quantity");
            Objects.requireNonNull(sourceOrderItemIds, "sourceOrderItemIds");
            sourceOrderItemIds = List.copyOf(sourceOrderItemIds);
        }
    }

    record MaterialRequirementView(
            UUID requirementId,
            List<MaterialRequirementSourceItemView> sourceItems,
            UUID destinationWarehouseId,
            Instant createdAt,
            Instant updatedAt,
            long version,
            MaterialRequirementStatusView status,
            Optional<Instant> submittedAt,
            Optional<String> submittedBy,
            List<MaterialRequirementLineView> lines) {
        public MaterialRequirementView {
            Objects.requireNonNull(requirementId, "requirementId");
            Objects.requireNonNull(sourceItems, "sourceItems");
            Objects.requireNonNull(destinationWarehouseId, "destinationWarehouseId");
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(updatedAt, "updatedAt");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(submittedAt, "submittedAt");
            Objects.requireNonNull(submittedBy, "submittedBy");
            Objects.requireNonNull(lines, "lines");
            sourceItems = List.copyOf(sourceItems);
            lines = List.copyOf(lines);
        }
    }

    /** One generated DRAFT Warehouse Transfer Document reference (Stage 3.5.10). */
    record GeneratedTransferDocumentView(
            UUID documentId, UUID sourceWarehouseId, UUID destinationWarehouseId) {
        public GeneratedTransferDocumentView {
            Objects.requireNonNull(documentId, "documentId");
            Objects.requireNonNull(sourceWarehouseId, "sourceWarehouseId");
            Objects.requireNonNull(destinationWarehouseId, "destinationWarehouseId");
        }
    }

    /**
     * Result of Submit. {@code created} is {@code true} for a fresh Submit, {@code false} for an
     * idempotent retry of an already-SUBMITTED requirement.
     */
    record SubmitMaterialRequirementResultView(
            UUID requirementId,
            long version,
            MaterialRequirementStatusView status,
            boolean created,
            List<GeneratedTransferDocumentView> documents) {
        public SubmitMaterialRequirementResultView {
            Objects.requireNonNull(requirementId, "requirementId");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(documents, "documents");
            documents = List.copyOf(documents);
        }
    }

    record WarehouseTransferRefView(
            UUID warehouseDraftOperationId, UUID materialReferenceId, BigDecimal quantity) {
        public WarehouseTransferRefView {
            Objects.requireNonNull(warehouseDraftOperationId, "warehouseDraftOperationId");
            Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            Objects.requireNonNull(quantity, "quantity");
        }
    }

    record LogicalTransferView(
            UUID id,
            UUID templateId,
            Instant createdAt,
            List<WarehouseTransferRefView> warehouseOperations) {
        public LogicalTransferView {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(templateId, "templateId");
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(warehouseOperations, "warehouseOperations");
            warehouseOperations = List.copyOf(warehouseOperations);
        }
    }

    enum ReceiptStatusView {
        RECEIVED,
        ALREADY_RECEIVED
    }

    record ReceiptResultView(ReceiptStatusView status, String message) {
        public ReceiptResultView {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(message, "message");
        }
    }

    record ItemReleaseView(UUID sourceOrderItemId, long releaseQuantity) {
        public ItemReleaseView {
            Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
        }
    }

    record CellAllocationView(UUID storageCellId, BigDecimal quantity) {
        public CellAllocationView {
            Objects.requireNonNull(storageCellId, "storageCellId");
            Objects.requireNonNull(quantity, "quantity");
        }
    }

    record MaterialActualUsageView(
            UUID sourceOrderItemId,
            UUID materialReferenceId,
            BigDecimal actualQuantity,
            List<CellAllocationView> allocations) {
        public MaterialActualUsageView {
            Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
            Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            Objects.requireNonNull(actualQuantity, "actualQuantity");
            Objects.requireNonNull(allocations, "allocations");
            allocations = List.copyOf(allocations);
        }
    }

    record PlannedMaterialLineView(
            UUID sourceOrderItemId,
            UUID materialReferenceId,
            UUID specificationId,
            BigDecimal plannedQuantity,
            MaterialPlanningSourceView planningSource,
            Optional<UUID> cuttingPlanId,
            Optional<String> materialName) {
        public PlannedMaterialLineView {
            Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
            Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            Objects.requireNonNull(specificationId, "specificationId");
            Objects.requireNonNull(plannedQuantity, "plannedQuantity");
            Objects.requireNonNull(planningSource, "planningSource");
            Objects.requireNonNull(cuttingPlanId, "cuttingPlanId");
            Objects.requireNonNull(materialName, "materialName");
        }
    }

    record MaterialActualDefaultView(
            UUID sourceOrderItemId,
            UUID materialReferenceId,
            BigDecimal plannedQuantity,
            BigDecimal actualQuantity) {
        public MaterialActualDefaultView {
            Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
            Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            Objects.requireNonNull(plannedQuantity, "plannedQuantity");
            Objects.requireNonNull(actualQuantity, "actualQuantity");
        }
    }

    record ReleasePreviewView(
            UUID sourceOrderId,
            List<ItemReleaseView> itemReleases,
            List<PlannedMaterialLineView> plannedMaterialLines,
            List<MaterialActualDefaultView> defaultActuals) {
        public ReleasePreviewView {
            Objects.requireNonNull(sourceOrderId, "sourceOrderId");
            Objects.requireNonNull(itemReleases, "itemReleases");
            Objects.requireNonNull(plannedMaterialLines, "plannedMaterialLines");
            Objects.requireNonNull(defaultActuals, "defaultActuals");
            itemReleases = List.copyOf(itemReleases);
            plannedMaterialLines = List.copyOf(plannedMaterialLines);
            defaultActuals = List.copyOf(defaultActuals);
        }
    }

    record ReleaseResultView(UUID documentId, UUID sourceOrderId, Instant releasedAt) {
        public ReleaseResultView {
            Objects.requireNonNull(documentId, "documentId");
            Objects.requireNonNull(sourceOrderId, "sourceOrderId");
            Objects.requireNonNull(releasedAt, "releasedAt");
        }
    }
}

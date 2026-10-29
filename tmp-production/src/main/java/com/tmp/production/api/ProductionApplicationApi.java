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
     * Live AVAILABLE snapshot on the Material Requirement destination (production) warehouse for
     * each line. Informational only — not subtracted from request quantity, not reserved, not
     * persisted. Requires {@code production.order.view} (no Warehouse stock permission).
     *
     * <p>{@code onProductionAvailable} is {@code null} when the Specification material is
     * unmatched or ambiguous; otherwise the summed AVAILABLE quantity (including zero).
     */
    List<MaterialRequirementLineProductionStockView> getMaterialRequirementProductionStock(
            UUID requirementId);

    /**
     * Lists persisted DRAFT Material Requirements newest-first for reopen UX. Requires
     * {@code production.order.view}. Requirement ids are for API reload only — UI must not show
     * them.
     */
    List<MaterialRequirementDraftSummaryView> listMaterialRequirementDrafts();

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
     * Atomically persists all provided DRAFT line request quantities for one Material Requirement
     * with optimistic concurrency. Does not submit Warehouse Demand or move stock.
     */
    MaterialRequirementView saveMaterialRequirementDraftQuantities(
            UUID requirementId,
            List<MaterialRequirementLineQuantityView> lineQuantities,
            long expectedVersion);

    /**
     * Physically deletes a DRAFT Material Requirement and its owned child rows. Refuses SUBMITTED
     * requirements and requirements with submission artifacts. Requires
     * {@code production.transfer.create}.
     */
    void deleteMaterialRequirementDraft(UUID requirementId, long expectedVersion);

    /**
     * Submits a DRAFT Material Requirement to the Warehouse (Stage 3.5.10): automatic source
     * routing + grouped DRAFT Transfer Documents in one ACID transaction. Idempotent for an
     * already-SUBMITTED requirement. Does not move physical stock.
     */
    SubmitMaterialRequirementResultView submitMaterialRequirement(
            UUID requirementId, long expectedVersion);

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
     * Batch Quantity Modes for the given orders. Missing stored settings return {@code STANDARD}
     * with {@code version 0}. Requires {@code production.order.view}. One backend call for
     * multi-order Material Request STEP 1.
     */
    List<OrderQuantityModeView> getOrderQuantityModes(List<UUID> orderIds);

    /**
     * Changes the order's Production quantity mode with optimistic concurrency and returns the
     * persisted state. Allowed in every Production state; never alters past releases or material
     * requirements. Requires {@code production.order.accept}.
     */
    OrderQuantityModeView changeOrderQuantityMode(
            UUID orderId, QuantityModeView quantityMode, long expectedVersion);

    /**
     * Side-effect-free material readiness for releasing the entire active remainder of the order.
     * Uses the Release material plan calculator and current AVAILABLE on the production warehouse.
     * Requires {@code production.order.view}. Does not write MATERIALS_CHECKED history.
     */
    MaterialReadinessView getOrderRemainingMaterialReadiness(UUID orderId);

    /**
     * Side-effect-free material readiness for an explicit future release quantity set (Phase 7
     * foundation). Requires {@code production.order.view}. Does not write MATERIALS_CHECKED
     * history.
     */
    MaterialReadinessView getMaterialReadinessForRelease(
            UUID orderId, List<ItemReleaseView> itemReleases);

    enum QuantityModeView {
        STANDARD,
        FLEXIBLE
    }

    enum MaterialReadinessStatusView {
        READY,
        NOT_READY,
        NO_PRODUCTION_WAREHOUSE,
        NOT_APPLICABLE,
        MATERIAL_REFERENCE_UNRESOLVED
    }

    enum MaterialReadinessReasonView {
        NONE,
        NOT_ACCEPTED,
        MANUFACTURED,
        CANCELLED,
        NO_RELEASABLE_QUANTITY,
        INSUFFICIENT_STOCK,
        NO_PRODUCTION_WAREHOUSE,
        MATERIAL_REFERENCE_UNRESOLVED
    }

    record MaterialReadinessLineView(
            UUID materialReferenceId,
            String materialCode,
            String materialName,
            String color,
            String unitOfMeasure,
            BigDecimal requiredQuantity,
            BigDecimal availableQuantity,
            BigDecimal shortageQuantity,
            BigDecimal lengthMm,
            BigDecimal technologicalPieceCount) {

        public MaterialReadinessLineView {
            Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            Objects.requireNonNull(materialCode, "materialCode");
            Objects.requireNonNull(materialName, "materialName");
            Objects.requireNonNull(color, "color");
            Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
            Objects.requireNonNull(requiredQuantity, "requiredQuantity");
            Objects.requireNonNull(availableQuantity, "availableQuantity");
            Objects.requireNonNull(shortageQuantity, "shortageQuantity");
        }
    }

    record MaterialReadinessView(
            MaterialReadinessStatusView status,
            MaterialReadinessReasonView reason,
            int deficientLineCount,
            List<MaterialReadinessLineView> lines) {

        public MaterialReadinessView {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(lines, "lines");
            if (deficientLineCount < 0) {
                throw new IllegalArgumentException("deficientLineCount must be >= 0");
            }
            lines = List.copyOf(lines);
        }
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

    /**
     * Material Requirement line read model. {@code requiredQuantity} is preliminary warehouse
     * demand («Требуется», sum of contributions). {@code quantity} is master request («К
     * запросу», ≥ 0, may differ from required). {@code technologicalPieceCount} is set for Rule A
     * meter rows with unambiguous length.
     */
    record MaterialRequirementLineView(
            UUID lineId,
            UUID materialReferenceId,
            String materialCode,
            String materialName,
            String color,
            String unitOfMeasure,
            BigDecimal lengthMm,
            BigDecimal requiredQuantity,
            BigDecimal quantity,
            List<UUID> sourceOrderItemIds,
            BigDecimal technologicalPieceCount) {
        public MaterialRequirementLineView {
            Objects.requireNonNull(lineId, "lineId");
            Objects.requireNonNull(materialCode, "materialCode");
            Objects.requireNonNull(color, "color");
            Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
            Objects.requireNonNull(requiredQuantity, "requiredQuantity");
            Objects.requireNonNull(quantity, "quantity");
            Objects.requireNonNull(sourceOrderItemIds, "sourceOrderItemIds");
            sourceOrderItemIds = List.copyOf(sourceOrderItemIds);
        }
    }

    /**
     * Read-side production-warehouse AVAILABLE for one Material Requirement line. {@code
     * onProductionAvailable == null} means unmatched/ambiguous MaterialReference (UI: "—").
     */
    record MaterialRequirementLineProductionStockView(
            UUID lineId, BigDecimal onProductionAvailable) {
        public MaterialRequirementLineProductionStockView {
            Objects.requireNonNull(lineId, "lineId");
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

    /** One source product row inside a DRAFT list summary (UUIDs for reopen / label resolution). */
    record MaterialRequirementDraftSourceSummaryView(
            UUID sourceOrderId, UUID sourceOrderItemId, long requestedProductQuantity) {
        public MaterialRequirementDraftSourceSummaryView {
            Objects.requireNonNull(sourceOrderId, "sourceOrderId");
            Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
            if (requestedProductQuantity <= 0L) {
                throw new IllegalArgumentException(
                        "requestedProductQuantity must be > 0: " + requestedProductQuantity);
            }
        }
    }

    /**
     * Human-facing DRAFT list row. {@code requirementId} / source UUIDs are for API reopen and label
     * resolution only — UI must not display raw UUIDs.
     */
    record MaterialRequirementDraftSummaryView(
            UUID requirementId,
            Instant createdAt,
            long version,
            List<MaterialRequirementDraftSourceSummaryView> sources) {
        public MaterialRequirementDraftSummaryView {
            Objects.requireNonNull(requirementId, "requirementId");
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(sources, "sources");
            if (sources.isEmpty()) {
                throw new IllegalArgumentException("sources must not be empty");
            }
            sources = List.copyOf(sources);
        }

        public int sourceItemCount() {
            return sources.size();
        }

        public int orderCount() {
            return (int)
                    sources.stream()
                            .map(MaterialRequirementDraftSourceSummaryView::sourceOrderId)
                            .distinct()
                            .count();
        }
    }

    /** One editable DRAFT line quantity for whole-draft Save. */
    record MaterialRequirementLineQuantityView(UUID lineId, BigDecimal quantity) {
        public MaterialRequirementLineQuantityView {
            Objects.requireNonNull(lineId, "lineId");
            Objects.requireNonNull(quantity, "quantity");
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

    /**
     * Confirmed actual consumption. {@code allocations} is accepted for backward compatibility
     * only; posting always allocates production cells server-side and ignores client cells.
     */
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
            Optional<String> materialName,
            String materialCode,
            String unitOfMeasure) {
        public PlannedMaterialLineView {
            Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
            Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            Objects.requireNonNull(specificationId, "specificationId");
            Objects.requireNonNull(plannedQuantity, "plannedQuantity");
            Objects.requireNonNull(planningSource, "planningSource");
            Objects.requireNonNull(cuttingPlanId, "cuttingPlanId");
            Objects.requireNonNull(materialName, "materialName");
            materialCode = materialCode == null ? "" : materialCode;
            unitOfMeasure = unitOfMeasure == null ? "" : unitOfMeasure;
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

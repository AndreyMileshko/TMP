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
     * Returns the configured Production destination warehouse id. Does not invent or select
     * warehouses; value comes from explicit runtime configuration.
     */
    DestinationWarehouseView destinationWarehouse();

    void acceptOrderIntoProduction(UUID orderId, String createdBy);

    /**
     * Runs the material availability check (persists MATERIALS_CHECKED history). Caller refreshes
     * the latest snapshot via {@link ProductionQueryApi#getMaterialAvailabilityResult}.
     */
    void checkMaterialAvailability(UUID orderId);

    /**
     * Creates a DRAFT Material Requirement from selected Order Items (Stage 3.5.9). Does not create
     * Warehouse transfers or mutate stock.
     */
    MaterialRequirementView prepareMaterialRequirement(
            UUID orderId, List<UUID> selectedOrderItemIds);

    /**
     * Edits one Material Requirement line quantity with optimistic concurrency (Stage 3.5.9).
     */
    MaterialRequirementView changeMaterialRequirementQuantity(
            UUID requirementId, UUID lineId, BigDecimal quantity, long expectedVersion);

    List<LogicalTransferView> listLogicalTransfers(UUID orderId);

    ReceiptResultView confirmMaterialReceipt(UUID logicalTransferId);

    ReleasePreviewView prepareRelease(UUID orderId, List<ItemReleaseView> itemReleases);

    ReleaseResultView releaseProducts(
            UUID orderId,
            List<ItemReleaseView> itemReleases,
            List<MaterialActualUsageView> materialActualUsages);

    void cancelOrderProduction(UUID orderId, Optional<String> reason);

    record DestinationWarehouseView(UUID productionWarehouseId) {
        public DestinationWarehouseView {
            Objects.requireNonNull(productionWarehouseId, "productionWarehouseId");
        }
    }

    enum MaterialRequirementStatusView {
        DRAFT
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
            UUID sourceOrderId,
            UUID destinationWarehouseId,
            Instant createdAt,
            Instant updatedAt,
            long version,
            MaterialRequirementStatusView status,
            List<MaterialRequirementLineView> lines) {
        public MaterialRequirementView {
            Objects.requireNonNull(requirementId, "requirementId");
            Objects.requireNonNull(sourceOrderId, "sourceOrderId");
            Objects.requireNonNull(destinationWarehouseId, "destinationWarehouseId");
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(updatedAt, "updatedAt");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(lines, "lines");
            lines = List.copyOf(lines);
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

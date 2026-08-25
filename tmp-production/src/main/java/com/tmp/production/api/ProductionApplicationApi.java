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
     * Returns the configured Production warehouse scope (main + production warehouse ids). Does not
     * invent or select warehouses; values come from explicit runtime configuration.
     */
    WarehouseScopeView warehouseScope();

    void acceptOrderIntoProduction(UUID orderId, String createdBy);

    /**
     * Runs the material availability check (persists MATERIALS_CHECKED history). Caller refreshes
     * the latest snapshot via {@link ProductionQueryApi#getMaterialAvailabilityResult}.
     */
    void checkMaterialAvailability(UUID orderId);

    TransferTemplateView prepareMaterialTransferTemplate(UUID orderId);

    TransferTemplateView changeTransferRequestedQuantity(
            UUID templateId, UUID lineId, BigDecimal quantity, long expectedVersion);

    TransferTemplateView excludeTransferLine(UUID templateId, UUID lineId, long expectedVersion);

    TransferTemplateView restoreTransferLine(UUID templateId, UUID lineId, long expectedVersion);

    LogicalTransferView confirmMaterialTransferCreate(
            UUID templateId, long expectedVersion, List<TransferCellAllocation> allocations);

    List<LogicalTransferView> listLogicalTransfers(UUID orderId);

    ReceiptResultView confirmMaterialReceipt(UUID logicalTransferId);

    ReleasePreviewView prepareRelease(UUID orderId, List<ItemReleaseView> itemReleases);

    ReleaseResultView releaseProducts(
            UUID orderId,
            List<ItemReleaseView> itemReleases,
            List<MaterialActualUsageView> materialActualUsages);

    void cancelOrderProduction(UUID orderId, Optional<String> reason);

    record WarehouseScopeView(UUID mainWarehouseId, UUID productionWarehouseId) {
        public WarehouseScopeView {
            Objects.requireNonNull(mainWarehouseId, "mainWarehouseId");
            Objects.requireNonNull(productionWarehouseId, "productionWarehouseId");
        }
    }

    enum TransferTemplateStatusView {
        DRAFT,
        CONFIRMED
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

    record TransferTemplateLineView(
            UUID lineId,
            UUID materialReferenceId,
            String materialCode,
            String materialName,
            String color,
            String unitOfMeasure,
            BigDecimal recommendedQuantity,
            BigDecimal requestedQuantity,
            boolean included,
            MaterialPlanningSourceView planningSource,
            Optional<UUID> cuttingPlanId,
            CuttingLinkStatusView cuttingLinkStatus,
            List<UUID> cuttingPlanReferences,
            List<UUID> sourceOrderItemIds,
            BigDecimal requiredQuantity,
            BigDecimal mainWarehouseAvailable,
            BigDecimal productionWarehouseAvailable,
            BigDecimal uncoveredDeficit) {
        public TransferTemplateLineView {
            Objects.requireNonNull(lineId, "lineId");
            Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            Objects.requireNonNull(materialCode, "materialCode");
            Objects.requireNonNull(materialName, "materialName");
            Objects.requireNonNull(color, "color");
            Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
            Objects.requireNonNull(recommendedQuantity, "recommendedQuantity");
            Objects.requireNonNull(requestedQuantity, "requestedQuantity");
            Objects.requireNonNull(planningSource, "planningSource");
            Objects.requireNonNull(cuttingPlanId, "cuttingPlanId");
            Objects.requireNonNull(cuttingLinkStatus, "cuttingLinkStatus");
            Objects.requireNonNull(cuttingPlanReferences, "cuttingPlanReferences");
            Objects.requireNonNull(sourceOrderItemIds, "sourceOrderItemIds");
            Objects.requireNonNull(requiredQuantity, "requiredQuantity");
            Objects.requireNonNull(mainWarehouseAvailable, "mainWarehouseAvailable");
            Objects.requireNonNull(productionWarehouseAvailable, "productionWarehouseAvailable");
            Objects.requireNonNull(uncoveredDeficit, "uncoveredDeficit");
            cuttingPlanReferences = List.copyOf(cuttingPlanReferences);
            sourceOrderItemIds = List.copyOf(sourceOrderItemIds);
        }
    }

    record TransferTemplateView(
            UUID templateId,
            UUID sourceOrderId,
            UUID sourceWarehouseId,
            UUID destinationWarehouseId,
            Instant createdAt,
            Instant updatedAt,
            long version,
            TransferTemplateStatusView status,
            Optional<Instant> confirmedAt,
            List<TransferTemplateLineView> lines) {
        public TransferTemplateView {
            Objects.requireNonNull(templateId, "templateId");
            Objects.requireNonNull(sourceOrderId, "sourceOrderId");
            Objects.requireNonNull(sourceWarehouseId, "sourceWarehouseId");
            Objects.requireNonNull(destinationWarehouseId, "destinationWarehouseId");
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(updatedAt, "updatedAt");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(confirmedAt, "confirmedAt");
            Objects.requireNonNull(lines, "lines");
            lines = List.copyOf(lines);
        }
    }

    record TransferCellAllocation(
            UUID templateLineId,
            UUID sourceStorageCellId,
            UUID destinationStorageCellId,
            BigDecimal quantity) {
        public TransferCellAllocation {
            Objects.requireNonNull(templateLineId, "templateLineId");
            Objects.requireNonNull(sourceStorageCellId, "sourceStorageCellId");
            Objects.requireNonNull(destinationStorageCellId, "destinationStorageCellId");
            Objects.requireNonNull(quantity, "quantity");
        }
    }

    record LogicalTransferView(UUID id, UUID templateId, Instant createdAt) {
        public LogicalTransferView {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(templateId, "templateId");
            Objects.requireNonNull(createdAt, "createdAt");
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

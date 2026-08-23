package com.tmp.production.application;

import com.tmp.production.application.ReleaseMaterialPlanBuilder.PlannedMaterialLine;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable result of a successful Release Products orchestration. */
public record ReleaseProductsResult(
        UUID documentId,
        UUID sourceOrderId,
        Instant releasedAt,
        List<ItemResult> itemResults,
        List<MaterialResult> materialResults,
        List<ConsumptionReference> consumptionReferences) {

    public ReleaseProductsResult {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        Objects.requireNonNull(releasedAt, "releasedAt");
        Objects.requireNonNull(itemResults, "itemResults");
        Objects.requireNonNull(materialResults, "materialResults");
        Objects.requireNonNull(consumptionReferences, "consumptionReferences");
        itemResults = List.copyOf(itemResults);
        materialResults = List.copyOf(materialResults);
        consumptionReferences = List.copyOf(consumptionReferences);
    }

    public record ItemResult(UUID sourceOrderItemId, long releaseQuantity) {

        public ItemResult {
            Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
        }
    }

    public record MaterialResult(
            UUID sourceOrderItemId,
            UUID materialReferenceId,
            BigDecimal plannedQuantity,
            BigDecimal actualQuantity) {

        public MaterialResult {
            Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
            Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            Objects.requireNonNull(plannedQuantity, "plannedQuantity");
            Objects.requireNonNull(actualQuantity, "actualQuantity");
        }
    }

    public record ConsumptionReference(
            UUID operationId,
            UUID materialReferenceId,
            UUID warehouseId,
            UUID storageCellId,
            BigDecimal quantity) {

        public ConsumptionReference {
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            Objects.requireNonNull(warehouseId, "warehouseId");
            Objects.requireNonNull(storageCellId, "storageCellId");
            Objects.requireNonNull(quantity, "quantity");
        }
    }

    /** Preview of system-computed plan with default actual = planned (no mutations). */
    public record PrepareReleasePreview(
            UUID sourceOrderId,
            List<ItemResult> itemReleases,
            List<PlannedMaterialLine> plannedMaterialLines,
            List<MaterialResult> defaultActuals) {

        public PrepareReleasePreview {
            Objects.requireNonNull(sourceOrderId, "sourceOrderId");
            Objects.requireNonNull(itemReleases, "itemReleases");
            Objects.requireNonNull(plannedMaterialLines, "plannedMaterialLines");
            Objects.requireNonNull(defaultActuals, "defaultActuals");
            itemReleases = List.copyOf(itemReleases);
            plannedMaterialLines = List.copyOf(plannedMaterialLines);
            defaultActuals = List.copyOf(defaultActuals);
        }
    }
}

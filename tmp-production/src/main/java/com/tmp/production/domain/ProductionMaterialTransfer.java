package com.tmp.production.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Production-owned logical grouping / traceability for one confirmed Material Transfer Template.
 *
 * <p>Not a Warehouse Transfer and not a Document Engine document.
 */
public final class ProductionMaterialTransfer {

    private final ProductionMaterialTransferId logicalTransferId;
    private final MaterialTransferTemplateId templateId;
    private final SourceOrderId sourceOrderId;
    private final Instant createdAt;
    private final List<WarehouseTransferOperationRef> warehouseOperationRefs;

    private ProductionMaterialTransfer(
            ProductionMaterialTransferId logicalTransferId,
            MaterialTransferTemplateId templateId,
            SourceOrderId sourceOrderId,
            Instant createdAt,
            List<WarehouseTransferOperationRef> warehouseOperationRefs) {
        this.logicalTransferId = Objects.requireNonNull(logicalTransferId, "logicalTransferId");
        this.templateId = Objects.requireNonNull(templateId, "templateId");
        this.sourceOrderId = Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.warehouseOperationRefs =
                List.copyOf(
                        Objects.requireNonNull(warehouseOperationRefs, "warehouseOperationRefs"));
        if (this.warehouseOperationRefs.isEmpty()) {
            throw new IllegalArgumentException("warehouseOperationRefs must not be empty");
        }
    }

    public static ProductionMaterialTransfer create(
            MaterialTransferTemplateId templateId,
            SourceOrderId sourceOrderId,
            Instant createdAt,
            List<WarehouseTransferOperationRef> warehouseOperationRefs) {
        return new ProductionMaterialTransfer(
                ProductionMaterialTransferId.generate(),
                templateId,
                sourceOrderId,
                createdAt,
                warehouseOperationRefs);
    }

    public static ProductionMaterialTransfer rehydrate(
            ProductionMaterialTransferId logicalTransferId,
            MaterialTransferTemplateId templateId,
            SourceOrderId sourceOrderId,
            Instant createdAt,
            List<WarehouseTransferOperationRef> warehouseOperationRefs) {
        return new ProductionMaterialTransfer(
                logicalTransferId, templateId, sourceOrderId, createdAt, warehouseOperationRefs);
    }

    public ProductionMaterialTransferId logicalTransferId() {
        return logicalTransferId;
    }

    public MaterialTransferTemplateId templateId() {
        return templateId;
    }

    public SourceOrderId sourceOrderId() {
        return sourceOrderId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public List<WarehouseTransferOperationRef> warehouseOperationRefs() {
        return warehouseOperationRefs;
    }
}

package com.tmp.production.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Production-owned opaque reference to one Warehouse Transfer DRAFT operation created from a
 * template line allocation.
 *
 * <p>Does not own Warehouse operation contents or lifecycle.
 */
public final class WarehouseTransferOperationRef {

    private final MaterialTransferTemplateLineId templateLineId;
    private final UUID warehouseDraftOperationId;
    private final MaterialReferenceId materialReferenceId;
    private final BigDecimal quantity;
    private final UUID sourceStorageCellId;
    private final UUID destinationStorageCellId;

    public WarehouseTransferOperationRef(
            MaterialTransferTemplateLineId templateLineId,
            UUID warehouseDraftOperationId,
            MaterialReferenceId materialReferenceId,
            BigDecimal quantity,
            UUID sourceStorageCellId,
            UUID destinationStorageCellId) {
        this.templateLineId = Objects.requireNonNull(templateLineId, "templateLineId");
        this.warehouseDraftOperationId =
                Objects.requireNonNull(warehouseDraftOperationId, "warehouseDraftOperationId");
        this.materialReferenceId =
                Objects.requireNonNull(materialReferenceId, "materialReferenceId");
        this.quantity = Objects.requireNonNull(quantity, "quantity");
        this.sourceStorageCellId =
                Objects.requireNonNull(sourceStorageCellId, "sourceStorageCellId");
        this.destinationStorageCellId =
                Objects.requireNonNull(destinationStorageCellId, "destinationStorageCellId");
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity must be > 0: " + quantity);
        }
    }

    public MaterialTransferTemplateLineId templateLineId() {
        return templateLineId;
    }

    public UUID warehouseDraftOperationId() {
        return warehouseDraftOperationId;
    }

    public MaterialReferenceId materialReferenceId() {
        return materialReferenceId;
    }

    public BigDecimal quantity() {
        return quantity;
    }

    public UUID sourceStorageCellId() {
        return sourceStorageCellId;
    }

    public UUID destinationStorageCellId() {
        return destinationStorageCellId;
    }
}

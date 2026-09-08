package com.tmp.warehouse.domain;

import java.util.Objects;

/**
 * One material line of a Warehouse-owned Transfer Document (Stage 3.5.2).
 *
 * <p>Quantity must be strictly positive. Line order is explicit and stable.
 */
public final class WarehouseTransferLine {

    private final WarehouseTransferLineId id;
    private final MaterialReferenceId materialReferenceId;
    private final StockQuantity quantity;
    private final int lineOrder;

    private WarehouseTransferLine(
            WarehouseTransferLineId id,
            MaterialReferenceId materialReferenceId,
            StockQuantity quantity,
            int lineOrder) {
        this.id = id;
        this.materialReferenceId = materialReferenceId;
        this.quantity = quantity;
        this.lineOrder = lineOrder;
    }

    public static WarehouseTransferLine of(
            WarehouseTransferLineId id,
            MaterialReferenceId materialReferenceId,
            StockQuantity quantity,
            int lineOrder) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(materialReferenceId, "materialReferenceId");
        Objects.requireNonNull(quantity, "quantity");
        if (quantity.value().signum() <= 0) {
            throw new IllegalArgumentException(
                    "Transfer line quantity must be positive: " + quantity.value());
        }
        if (lineOrder < 1) {
            throw new IllegalArgumentException("lineOrder must be >= 1: " + lineOrder);
        }
        return new WarehouseTransferLine(id, materialReferenceId, quantity, lineOrder);
    }

    public WarehouseTransferLineId id() {
        return id;
    }

    public MaterialReferenceId materialReferenceId() {
        return materialReferenceId;
    }

    public StockQuantity quantity() {
        return quantity;
    }

    public int lineOrder() {
        return lineOrder;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WarehouseTransferLine that)) {
            return false;
        }
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}

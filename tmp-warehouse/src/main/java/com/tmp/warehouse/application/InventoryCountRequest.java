package com.tmp.warehouse.application;

import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.WarehouseId;
import java.util.Objects;

/**
 * Inventory count request: recorded physical quantity for reconciliation (Specification §11).
 *
 * <p>Warehouse computes the difference against the system balance and applies an {@link
 * AdjustmentRequest} when they differ. Does not implement batch/FIFO/FEFO strategies.
 */
public record InventoryCountRequest(
        MaterialReference material,
        StockQuantity countedQuantity,
        WarehouseId warehouseId,
        StorageCellId storageCellId) {

    public InventoryCountRequest {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(countedQuantity, "countedQuantity");
        Objects.requireNonNull(warehouseId, "warehouseId");
        Objects.requireNonNull(storageCellId, "storageCellId");
    }
}

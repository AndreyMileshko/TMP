package com.tmp.warehouse.application;

import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.WarehouseId;
import java.util.Objects;

/**
 * Consumption request: material is withdrawn from a warehouse storage cell (Specification §14).
 *
 * <p>Production determines material and quantity; Warehouse validates availability and executes the
 * operation. Does not calculate production demand.
 */
public record ConsumptionRequest(
        MaterialReference material,
        StockQuantity quantity,
        WarehouseId warehouseId,
        StorageCellId storageCellId) {

    public ConsumptionRequest {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(warehouseId, "warehouseId");
        Objects.requireNonNull(storageCellId, "storageCellId");
        if (quantity.value().signum() <= 0) {
            throw new IllegalArgumentException(
                    "Consumption quantity must be positive: " + quantity.value());
        }
    }
}

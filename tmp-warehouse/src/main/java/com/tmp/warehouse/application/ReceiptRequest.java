package com.tmp.warehouse.application;

import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.WarehouseId;
import java.util.Objects;

/**
 * Receipt request: material arrives into a warehouse storage cell (Specification §12).
 *
 * <p>Does not include supplier, procurement or price data.
 */
public record ReceiptRequest(
        MaterialReference material,
        StockQuantity quantity,
        WarehouseId warehouseId,
        StorageCellId storageCellId) {

    public ReceiptRequest {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(warehouseId, "warehouseId");
        Objects.requireNonNull(storageCellId, "storageCellId");
        if (quantity.value().signum() <= 0) {
            throw new IllegalArgumentException(
                    "Receipt quantity must be positive: " + quantity.value());
        }
    }
}

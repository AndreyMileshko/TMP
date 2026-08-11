package com.tmp.warehouse.application;

import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.WarehouseId;
import java.util.Objects;

/**
 * Receipt request: material arrives into a warehouse storage cell (Specification §12).
 *
 * <p>Creates or reuses a warehouse-owned {@link com.tmp.warehouse.domain.MaterialReference} by
 * natural key ({@code article + color + size + unitOfMeasure}). Does not include supplier,
 * procurement or price data.
 */
public record ReceiptRequest(
        String article,
        String name,
        String color,
        String size,
        String unitOfMeasure,
        StockQuantity quantity,
        WarehouseId warehouseId,
        StorageCellId storageCellId) {

    public ReceiptRequest {
        Objects.requireNonNull(article, "article");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(warehouseId, "warehouseId");
        Objects.requireNonNull(storageCellId, "storageCellId");
        if (quantity.value().signum() <= 0) {
            throw new IllegalArgumentException(
                    "Receipt quantity must be positive: " + quantity.value());
        }
    }
}

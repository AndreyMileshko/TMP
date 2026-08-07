package com.tmp.warehouse.application;

import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.WarehouseId;
import java.util.Objects;

/**
 * Transfer receive request: receives {@code IN_TRANSIT} stock into destination warehouse AVAILABLE
 * stock (Specification §13.2). Source and destination warehouses must differ.
 */
public record TransferReceiveRequest(
        MaterialReference material,
        StockQuantity quantity,
        WarehouseId sourceWarehouseId,
        StorageCellId sourceCellId,
        WarehouseId destinationWarehouseId,
        StorageCellId destinationCellId) {

    public TransferReceiveRequest {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(sourceWarehouseId, "sourceWarehouseId");
        Objects.requireNonNull(sourceCellId, "sourceCellId");
        Objects.requireNonNull(destinationWarehouseId, "destinationWarehouseId");
        Objects.requireNonNull(destinationCellId, "destinationCellId");
        if (quantity.value().signum() <= 0) {
            throw new IllegalArgumentException(
                    "Transfer receive quantity must be positive: " + quantity.value());
        }
    }
}

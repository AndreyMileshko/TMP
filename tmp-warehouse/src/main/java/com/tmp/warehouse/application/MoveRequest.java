package com.tmp.warehouse.application;

import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.WarehouseId;
import java.util.Objects;

/**
 * Internal move request: material relocates between storage cells (Specification §13.1).
 *
 * <p>Does not change material identity or total quantity. Cross-warehouse moves are rejected by
 * {@link WarehouseMoveService} (Transfer is out of scope).
 */
public record MoveRequest(
        MaterialReference material,
        StockQuantity quantity,
        WarehouseId sourceWarehouseId,
        StorageCellId sourceCellId,
        WarehouseId destinationWarehouseId,
        StorageCellId destinationCellId) {

    public MoveRequest {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(sourceWarehouseId, "sourceWarehouseId");
        Objects.requireNonNull(sourceCellId, "sourceCellId");
        Objects.requireNonNull(destinationWarehouseId, "destinationWarehouseId");
        Objects.requireNonNull(destinationCellId, "destinationCellId");
        if (quantity.value().signum() <= 0) {
            throw new IllegalArgumentException(
                    "Move quantity must be positive: " + quantity.value());
        }
    }
}

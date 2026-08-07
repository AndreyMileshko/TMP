package com.tmp.warehouse.application;

import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.WarehouseId;
import java.util.Objects;

/**
 * Transfer send request: ships stock from source warehouse into {@code IN_TRANSIT} (Specification
 * §13.2). Destination warehouse must differ from source; receive completes the transfer later.
 */
public record TransferSendRequest(
        MaterialReference material,
        StockQuantity quantity,
        WarehouseId sourceWarehouseId,
        StorageCellId sourceCellId,
        WarehouseId destinationWarehouseId) {

    public TransferSendRequest {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(sourceWarehouseId, "sourceWarehouseId");
        Objects.requireNonNull(sourceCellId, "sourceCellId");
        Objects.requireNonNull(destinationWarehouseId, "destinationWarehouseId");
        if (quantity.value().signum() <= 0) {
            throw new IllegalArgumentException(
                    "Transfer send quantity must be positive: " + quantity.value());
        }
    }
}

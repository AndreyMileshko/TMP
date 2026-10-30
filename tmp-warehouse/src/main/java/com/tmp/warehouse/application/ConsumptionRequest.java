package com.tmp.warehouse.application;

import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.OperationOrigin;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.WarehouseId;
import java.util.Objects;
import java.util.Optional;

/**
 * Consumption request: material is withdrawn from a warehouse storage cell (Specification §14).
 *
 * <p>Production determines material and quantity; Warehouse validates availability and executes the
 * operation. Optional {@link OperationOrigin} is opaque informational metadata and does not affect
 * stock accounting. Does not calculate production demand.
 */
public record ConsumptionRequest(
        MaterialReference material,
        StockQuantity quantity,
        WarehouseId warehouseId,
        StorageCellId storageCellId,
        OperationOrigin origin) {

    public ConsumptionRequest(
            MaterialReference material,
            StockQuantity quantity,
            WarehouseId warehouseId,
            StorageCellId storageCellId) {
        this(material, quantity, warehouseId, storageCellId, null);
    }

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

    public Optional<OperationOrigin> originOptional() {
        return Optional.ofNullable(origin);
    }
}

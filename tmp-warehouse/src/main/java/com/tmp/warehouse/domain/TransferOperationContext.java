package com.tmp.warehouse.domain;

import java.util.Objects;

/** Destination warehouse/cell for a TRANSFER draft or completed send operation. */
public final class TransferOperationContext {

    private final WarehouseOperationId operationId;
    private final WarehouseId destinationWarehouseId;
    private final StorageCellId destinationStorageCellId;

    public TransferOperationContext(
            WarehouseOperationId operationId,
            WarehouseId destinationWarehouseId,
            StorageCellId destinationStorageCellId) {
        this.operationId = Objects.requireNonNull(operationId, "operationId");
        this.destinationWarehouseId =
                Objects.requireNonNull(destinationWarehouseId, "destinationWarehouseId");
        this.destinationStorageCellId =
                Objects.requireNonNull(destinationStorageCellId, "destinationStorageCellId");
    }

    public WarehouseOperationId operationId() {
        return operationId;
    }

    public WarehouseId destinationWarehouseId() {
        return destinationWarehouseId;
    }

    public StorageCellId destinationStorageCellId() {
        return destinationStorageCellId;
    }
}

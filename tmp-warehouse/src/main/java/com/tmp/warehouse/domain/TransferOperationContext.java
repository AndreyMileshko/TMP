package com.tmp.warehouse.domain;

import java.util.Objects;

/**
 * Destination warehouse/cell for a TRANSFER draft or completed send, plus optional one-time receive
 * link.
 */
public final class TransferOperationContext {

    private final WarehouseOperationId operationId;
    private final WarehouseId destinationWarehouseId;
    private final StorageCellId destinationStorageCellId;
    private final WarehouseOperationId receiveOperationId;

    public TransferOperationContext(
            WarehouseOperationId operationId,
            WarehouseId destinationWarehouseId,
            StorageCellId destinationStorageCellId) {
        this(operationId, destinationWarehouseId, destinationStorageCellId, null);
    }

    public TransferOperationContext(
            WarehouseOperationId operationId,
            WarehouseId destinationWarehouseId,
            StorageCellId destinationStorageCellId,
            WarehouseOperationId receiveOperationId) {
        this.operationId = Objects.requireNonNull(operationId, "operationId");
        this.destinationWarehouseId =
                Objects.requireNonNull(destinationWarehouseId, "destinationWarehouseId");
        this.destinationStorageCellId =
                Objects.requireNonNull(destinationStorageCellId, "destinationStorageCellId");
        this.receiveOperationId = receiveOperationId;
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

    public WarehouseOperationId receiveOperationId() {
        return receiveOperationId;
    }

    public boolean isReceived() {
        return receiveOperationId != null;
    }

    public TransferOperationContext withReceiveOperationId(WarehouseOperationId receiveId) {
        return new TransferOperationContext(
                operationId,
                destinationWarehouseId,
                destinationStorageCellId,
                Objects.requireNonNull(receiveId, "receiveId"));
    }
}

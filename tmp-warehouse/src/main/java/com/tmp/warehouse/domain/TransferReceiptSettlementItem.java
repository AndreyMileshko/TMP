package com.tmp.warehouse.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One physical receive segment: send allocation → destination cell → TRANSFER_RECEIVE (Stage
 * 3.5.8.1).
 */
public final class TransferReceiptSettlementItem {

    private final UUID id;
    private final UUID documentId;
    private final UUID sendAllocationId;
    private final StorageCellId destinationStorageCellId;
    private final StockQuantity quantity;
    private final WarehouseOperationId receiveOperationId;
    private final Instant createdAt;

    private TransferReceiptSettlementItem(
            UUID id,
            UUID documentId,
            UUID sendAllocationId,
            StorageCellId destinationStorageCellId,
            StockQuantity quantity,
            WarehouseOperationId receiveOperationId,
            Instant createdAt) {
        this.id = id;
        this.documentId = documentId;
        this.sendAllocationId = sendAllocationId;
        this.destinationStorageCellId = destinationStorageCellId;
        this.quantity = quantity;
        this.receiveOperationId = receiveOperationId;
        this.createdAt = createdAt;
    }

    public static TransferReceiptSettlementItem of(
            UUID id,
            UUID documentId,
            UUID sendAllocationId,
            StorageCellId destinationStorageCellId,
            StockQuantity quantity,
            WarehouseOperationId receiveOperationId,
            Instant createdAt) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(sendAllocationId, "sendAllocationId");
        Objects.requireNonNull(destinationStorageCellId, "destinationStorageCellId");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(receiveOperationId, "receiveOperationId");
        Objects.requireNonNull(createdAt, "createdAt");
        if (quantity.value().signum() <= 0) {
            throw new IllegalArgumentException(
                    "Receipt settlement quantity must be positive: " + quantity.value());
        }
        return new TransferReceiptSettlementItem(
                id,
                documentId,
                sendAllocationId,
                destinationStorageCellId,
                quantity,
                receiveOperationId,
                createdAt);
    }

    public UUID id() {
        return id;
    }

    public UUID documentId() {
        return documentId;
    }

    public UUID sendAllocationId() {
        return sendAllocationId;
    }

    public StorageCellId destinationStorageCellId() {
        return destinationStorageCellId;
    }

    public StockQuantity quantity() {
        return quantity;
    }

    public WarehouseOperationId receiveOperationId() {
        return receiveOperationId;
    }

    public Instant createdAt() {
        return createdAt;
    }
}

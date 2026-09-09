package com.tmp.warehouse.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One physical return segment: send allocation → source return cell → TRANSFER_RETURN (Stage
 * 3.5.8.3).
 */
public final class TransferReturnSettlementItem {

    private final UUID id;
    private final UUID documentId;
    private final UUID sendAllocationId;
    private final StorageCellId returnStorageCellId;
    private final StockQuantity quantity;
    private final WarehouseOperationId returnOperationId;
    private final Instant createdAt;

    private TransferReturnSettlementItem(
            UUID id,
            UUID documentId,
            UUID sendAllocationId,
            StorageCellId returnStorageCellId,
            StockQuantity quantity,
            WarehouseOperationId returnOperationId,
            Instant createdAt) {
        this.id = id;
        this.documentId = documentId;
        this.sendAllocationId = sendAllocationId;
        this.returnStorageCellId = returnStorageCellId;
        this.quantity = quantity;
        this.returnOperationId = returnOperationId;
        this.createdAt = createdAt;
    }

    public static TransferReturnSettlementItem of(
            UUID id,
            UUID documentId,
            UUID sendAllocationId,
            StorageCellId returnStorageCellId,
            StockQuantity quantity,
            WarehouseOperationId returnOperationId,
            Instant createdAt) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(sendAllocationId, "sendAllocationId");
        Objects.requireNonNull(returnStorageCellId, "returnStorageCellId");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(returnOperationId, "returnOperationId");
        Objects.requireNonNull(createdAt, "createdAt");
        if (quantity.value().signum() <= 0) {
            throw new IllegalArgumentException(
                    "Return settlement quantity must be positive: " + quantity.value());
        }
        return new TransferReturnSettlementItem(
                id,
                documentId,
                sendAllocationId,
                returnStorageCellId,
                quantity,
                returnOperationId,
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

    public StorageCellId returnStorageCellId() {
        return returnStorageCellId;
    }

    public StockQuantity quantity() {
        return quantity;
    }

    public WarehouseOperationId returnOperationId() {
        return returnOperationId;
    }

    public Instant createdAt() {
        return createdAt;
    }
}

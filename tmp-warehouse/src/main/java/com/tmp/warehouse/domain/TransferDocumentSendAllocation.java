package com.tmp.warehouse.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Warehouse-owned send execution link: Transfer Document line → source cell → TRANSFER_SEND
 * operation (Stage 3.5.6). Immutable after successful POST; {@code sendOperationId} is stamped
 * during {@code onPost}.
 */
public final class TransferDocumentSendAllocation {

    private final UUID id;
    private final UUID documentId;
    private final WarehouseTransferLineId lineId;
    private final StorageCellId sourceStorageCellId;
    private final StockQuantity quantity;
    private final WarehouseOperationId sendOperationId;
    private final Instant createdAt;

    private TransferDocumentSendAllocation(
            UUID id,
            UUID documentId,
            WarehouseTransferLineId lineId,
            StorageCellId sourceStorageCellId,
            StockQuantity quantity,
            WarehouseOperationId sendOperationId,
            Instant createdAt) {
        this.id = id;
        this.documentId = documentId;
        this.lineId = lineId;
        this.sourceStorageCellId = sourceStorageCellId;
        this.quantity = quantity;
        this.sendOperationId = sendOperationId;
        this.createdAt = createdAt;
    }

    public static TransferDocumentSendAllocation pending(
            UUID id,
            UUID documentId,
            WarehouseTransferLineId lineId,
            StorageCellId sourceStorageCellId,
            StockQuantity quantity,
            Instant createdAt) {
        return of(id, documentId, lineId, sourceStorageCellId, quantity, null, createdAt);
    }

    public static TransferDocumentSendAllocation of(
            UUID id,
            UUID documentId,
            WarehouseTransferLineId lineId,
            StorageCellId sourceStorageCellId,
            StockQuantity quantity,
            WarehouseOperationId sendOperationId,
            Instant createdAt) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(lineId, "lineId");
        Objects.requireNonNull(sourceStorageCellId, "sourceStorageCellId");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(createdAt, "createdAt");
        if (quantity.value().signum() <= 0) {
            throw new IllegalArgumentException(
                    "Send allocation quantity must be positive: " + quantity.value());
        }
        return new TransferDocumentSendAllocation(
                id,
                documentId,
                lineId,
                sourceStorageCellId,
                quantity,
                sendOperationId,
                createdAt);
    }

    public TransferDocumentSendAllocation withSendOperationId(WarehouseOperationId operationId) {
        if (sendOperationId != null) {
            throw new InvalidWarehouseStateException(
                    "Send allocation already linked to operation: allocationId="
                            + id
                            + ", sendOperationId="
                            + sendOperationId);
        }
        return of(
                id,
                documentId,
                lineId,
                sourceStorageCellId,
                quantity,
                Objects.requireNonNull(operationId, "operationId"),
                createdAt);
    }

    public UUID id() {
        return id;
    }

    public UUID documentId() {
        return documentId;
    }

    public WarehouseTransferLineId lineId() {
        return lineId;
    }

    public StorageCellId sourceStorageCellId() {
        return sourceStorageCellId;
    }

    public StockQuantity quantity() {
        return quantity;
    }

    public Optional<WarehouseOperationId> sendOperationIdOptional() {
        return Optional.ofNullable(sendOperationId);
    }

    public WarehouseOperationId sendOperationId() {
        return sendOperationId;
    }

    public boolean hasSendOperation() {
        return sendOperationId != null;
    }

    public Instant createdAt() {
        return createdAt;
    }
}

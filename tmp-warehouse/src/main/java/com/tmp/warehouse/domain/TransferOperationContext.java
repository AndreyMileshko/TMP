package com.tmp.warehouse.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Destination warehouse (always required) and optional destination cell for a TRANSFER draft or
 * completed send, plus optional one-time receive link.
 *
 * <p>Legacy contexts store a destination cell at draft time. Deferred-destination contexts leave
 * the cell absent until receive selects it.
 */
public final class TransferOperationContext {

    private final WarehouseOperationId operationId;
    private final WarehouseId destinationWarehouseId;
    private final StorageCellId destinationStorageCellId;
    private final WarehouseOperationId receiveOperationId;

    /** Legacy context with a preselected destination cell. */
    public TransferOperationContext(
            WarehouseOperationId operationId,
            WarehouseId destinationWarehouseId,
            StorageCellId destinationStorageCellId) {
        this(
                operationId,
                destinationWarehouseId,
                Objects.requireNonNull(destinationStorageCellId, "destinationStorageCellId"),
                null);
    }

    /**
     * Full constructor. {@code destinationStorageCellId} may be {@code null} for deferred
     * destination until receive.
     */
    public TransferOperationContext(
            WarehouseOperationId operationId,
            WarehouseId destinationWarehouseId,
            StorageCellId destinationStorageCellId,
            WarehouseOperationId receiveOperationId) {
        this.operationId = Objects.requireNonNull(operationId, "operationId");
        this.destinationWarehouseId =
                Objects.requireNonNull(destinationWarehouseId, "destinationWarehouseId");
        this.destinationStorageCellId = destinationStorageCellId;
        this.receiveOperationId = receiveOperationId;
    }

    /** Deferred-destination context: warehouse known, cell selected at receive. */
    public static TransferOperationContext deferredDestination(
            WarehouseOperationId operationId, WarehouseId destinationWarehouseId) {
        return new TransferOperationContext(operationId, destinationWarehouseId, null, null);
    }

    public WarehouseOperationId operationId() {
        return operationId;
    }

    public WarehouseId destinationWarehouseId() {
        return destinationWarehouseId;
    }

    /**
     * Destination cell when known; {@code null} when deferred until receive.
     *
     * <p>Prefer {@link #destinationStorageCellIdOptional()} to avoid accidental NPE.
     */
    public StorageCellId destinationStorageCellId() {
        return destinationStorageCellId;
    }

    public Optional<StorageCellId> destinationStorageCellIdOptional() {
        return Optional.ofNullable(destinationStorageCellId);
    }

    public boolean hasDestinationStorageCell() {
        return destinationStorageCellId != null;
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

    /**
     * Records successful receive with the actual destination cell (sets cell when previously
     * deferred).
     */
    public TransferOperationContext withReceiveClaim(
            WarehouseOperationId receiveId, StorageCellId actualDestinationCellId) {
        return new TransferOperationContext(
                operationId,
                destinationWarehouseId,
                Objects.requireNonNull(actualDestinationCellId, "actualDestinationCellId"),
                Objects.requireNonNull(receiveId, "receiveId"));
    }
}

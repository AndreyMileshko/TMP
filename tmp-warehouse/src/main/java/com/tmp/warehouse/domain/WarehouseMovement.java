package com.tmp.warehouse.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable warehouse movement history record (Specification §9).
 *
 * <p>Created when stock quantity and/or state changes. Never updated or deleted after creation.
 */
public final class WarehouseMovement {

    private final WarehouseMovementId id;
    private final WarehouseOperationId operationId;
    private final WarehouseId warehouseId;
    private final StorageCellId storageCellId;
    private final MaterialReference material;
    private final StockState previousState;
    private final StockState newState;
    private final StockQuantity previousQuantity;
    private final StockQuantity newQuantity;
    private final Instant occurredAt;

    private WarehouseMovement(
            WarehouseMovementId id,
            WarehouseOperationId operationId,
            WarehouseId warehouseId,
            StorageCellId storageCellId,
            MaterialReference material,
            StockState previousState,
            StockState newState,
            StockQuantity previousQuantity,
            StockQuantity newQuantity,
            Instant occurredAt) {
        this.id = id;
        this.operationId = operationId;
        this.warehouseId = warehouseId;
        this.storageCellId = storageCellId;
        this.material = material;
        this.previousState = previousState;
        this.newState = newState;
        this.previousQuantity = previousQuantity;
        this.newQuantity = newQuantity;
        this.occurredAt = occurredAt;
    }

    public static WarehouseMovement record(
            WarehouseMovementId id,
            WarehouseOperationId operationId,
            WarehouseId warehouseId,
            StorageCellId storageCellId,
            MaterialReference material,
            StockState previousState,
            StockState newState,
            StockQuantity previousQuantity,
            StockQuantity newQuantity,
            Instant occurredAt) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(warehouseId, "warehouseId");
        Objects.requireNonNull(storageCellId, "storageCellId");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(newState, "newState");
        Objects.requireNonNull(newQuantity, "newQuantity");
        Objects.requireNonNull(occurredAt, "occurredAt");
        return new WarehouseMovement(
                id,
                operationId,
                warehouseId,
                storageCellId,
                material,
                previousState,
                newState,
                previousQuantity,
                newQuantity,
                occurredAt);
    }

    public WarehouseMovementId id() {
        return id;
    }

    public WarehouseOperationId operationId() {
        return operationId;
    }

    public WarehouseId warehouseId() {
        return warehouseId;
    }

    public StorageCellId storageCellId() {
        return storageCellId;
    }

    public MaterialReference material() {
        return material;
    }

    public StockState previousState() {
        return previousState;
    }

    public StockState newState() {
        return newState;
    }

    public StockQuantity previousQuantity() {
        return previousQuantity;
    }

    public StockQuantity newQuantity() {
        return newQuantity;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WarehouseMovement that)) {
            return false;
        }
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "WarehouseMovement{id="
                + id
                + ", operationId="
                + operationId
                + ", newState="
                + newState
                + ", newQuantity="
                + newQuantity
                + '}';
    }
}

package com.tmp.warehouse.domain;

import java.util.Objects;

/**
 * Descriptive warehouse operation (Specification §10 / §11).
 *
 * <p>This type describes a warehouse operation and is the sole allowed write collaborator for
 * {@link StockPosition} changes. Full business execution (Receipt/Move/Transfer flows) is out of
 * scope for STAGE6-002.
 */
public final class WarehouseOperation {

    private final WarehouseOperationId id;
    private final WarehouseOperationType type;
    private final MaterialReference material;
    private final WarehouseId warehouseId;
    private final StorageCellId storageCellId;
    private final StockQuantity quantity;

    private WarehouseOperation(
            WarehouseOperationId id,
            WarehouseOperationType type,
            MaterialReference material,
            WarehouseId warehouseId,
            StorageCellId storageCellId,
            StockQuantity quantity) {
        this.id = id;
        this.type = type;
        this.material = material;
        this.warehouseId = warehouseId;
        this.storageCellId = storageCellId;
        this.quantity = quantity;
    }

    /**
     * Creates an operation description without executing stock changes.
     */
    public static WarehouseOperation describe(
            WarehouseOperationId id,
            WarehouseOperationType type,
            MaterialReference material,
            WarehouseId warehouseId,
            StorageCellId storageCellId,
            StockQuantity quantity) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(warehouseId, "warehouseId");
        Objects.requireNonNull(storageCellId, "storageCellId");
        Objects.requireNonNull(quantity, "quantity");
        return new WarehouseOperation(id, type, material, warehouseId, storageCellId, quantity);
    }

    /**
     * Applies a stock quantity/state change through the operation write path.
     *
     * <p>Does not implement Receipt/Move/Transfer business flows; only enforces that stock
     * mutation goes via {@code WarehouseOperation}.
     */
    public StockPosition applyTo(
            StockPosition position, StockState newState, StockQuantity newQuantity) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(newState, "newState");
        Objects.requireNonNull(newQuantity, "newQuantity");
        requireMatchingPosition(position);
        return position.applyChange(newState, newQuantity);
    }

    /**
     * Creates an initial stock position for this operation target.
     */
    public StockPosition createPosition(StockState state) {
        Objects.requireNonNull(state, "state");
        return StockPosition.of(
                StockPositionId.generate(), warehouseId, storageCellId, material, state, quantity);
    }

    private void requireMatchingPosition(StockPosition position) {
        if (!warehouseId.equals(position.warehouseId())
                || !storageCellId.equals(position.storageCellId())
                || !material.equals(position.material())) {
            throw new InvalidWarehouseStateException(
                    "Warehouse operation target does not match stock position: operationId=" + id);
        }
    }

    public WarehouseOperationId id() {
        return id;
    }

    public WarehouseOperationType type() {
        return type;
    }

    public MaterialReference material() {
        return material;
    }

    public WarehouseId warehouseId() {
        return warehouseId;
    }

    public StorageCellId storageCellId() {
        return storageCellId;
    }

    public StockQuantity quantity() {
        return quantity;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WarehouseOperation that)) {
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
        return "WarehouseOperation{id=" + id + ", type=" + type + '}';
    }
}

package com.tmp.warehouse.domain;

import java.util.Objects;

/**
 * Current stock balance for a material in a warehouse cell and stock state (Specification §6).
 *
 * <p>Identity key: {@code Warehouse + Storage Cell + Material + Stock State}. History is not stored
 * here. Direct mutation is forbidden: quantity/state changes are applied only through
 * {@link WarehouseOperation} (package-private write path).
 */
public final class StockPosition {

    private final WarehouseId warehouseId;
    private final StorageCellId storageCellId;
    private final MaterialReference material;
    private final StockState stockState;
    private final StockQuantity quantity;

    private StockPosition(
            WarehouseId warehouseId,
            StorageCellId storageCellId,
            MaterialReference material,
            StockState stockState,
            StockQuantity quantity) {
        this.warehouseId = warehouseId;
        this.storageCellId = storageCellId;
        this.material = material;
        this.stockState = stockState;
        this.quantity = quantity;
    }

    /**
     * Creates a stock position. Intended for Warehouse Operation write path and rehydration.
     *
     * <p>Public callers outside the Warehouse domain must not mutate positions; use
     * {@link WarehouseOperation} as the only write mechanism.
     */
    public static StockPosition of(
            WarehouseId warehouseId,
            StorageCellId storageCellId,
            MaterialReference material,
            StockState stockState,
            StockQuantity quantity) {
        Objects.requireNonNull(warehouseId, "warehouseId");
        Objects.requireNonNull(storageCellId, "storageCellId");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(stockState, "stockState");
        Objects.requireNonNull(quantity, "quantity");
        return new StockPosition(warehouseId, storageCellId, material, stockState, quantity);
    }

    /**
     * Applies a quantity/state change produced by a warehouse operation. Package-private so that
     * only same-package collaborators ({@link WarehouseOperation}) can mutate stock positions.
     */
    StockPosition applyChange(StockState newState, StockQuantity newQuantity) {
        Objects.requireNonNull(newState, "newState");
        Objects.requireNonNull(newQuantity, "newQuantity");
        return new StockPosition(warehouseId, storageCellId, material, newState, newQuantity);
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

    public StockState stockState() {
        return stockState;
    }

    public StockQuantity quantity() {
        return quantity;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StockPosition that)) {
            return false;
        }
        return warehouseId.equals(that.warehouseId)
                && storageCellId.equals(that.storageCellId)
                && material.equals(that.material)
                && stockState == that.stockState;
    }

    @Override
    public int hashCode() {
        return Objects.hash(warehouseId, storageCellId, material, stockState);
    }

    @Override
    public String toString() {
        return "StockPosition{warehouseId="
                + warehouseId
                + ", storageCellId="
                + storageCellId
                + ", material="
                + material
                + ", stockState="
                + stockState
                + ", quantity="
                + quantity
                + '}';
    }
}

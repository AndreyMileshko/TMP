package com.tmp.warehouse.domain;

import java.util.Objects;

/**
 * Current stock balance for a material in a warehouse cell and stock state (Specification §6).
 *
 * <p>Business identity key: {@code Warehouse + Storage Cell + Material + Stock State}. History is
 * not stored here. Direct business mutation is forbidden: quantity/state changes are applied only
 * through {@link WarehouseOperation} (package-private write path). Persistence adapters may
 * rehydrate and store the current snapshot.
 */
public final class StockPosition {

    private final StockPositionId id;
    private final WarehouseId warehouseId;
    private final StorageCellId storageCellId;
    private final MaterialReference material;
    private final StockState stockState;
    private final StockQuantity quantity;
    private final long version;

    private StockPosition(
            StockPositionId id,
            WarehouseId warehouseId,
            StorageCellId storageCellId,
            MaterialReference material,
            StockState stockState,
            StockQuantity quantity,
            long version) {
        this.id = id;
        this.warehouseId = warehouseId;
        this.storageCellId = storageCellId;
        this.material = material;
        this.stockState = stockState;
        this.quantity = quantity;
        this.version = version;
    }

    /**
     * Creates a new stock position with a generated id and version {@code 0}.
     */
    public static StockPosition of(
            WarehouseId warehouseId,
            StorageCellId storageCellId,
            MaterialReference material,
            StockState stockState,
            StockQuantity quantity) {
        return of(StockPositionId.generate(), warehouseId, storageCellId, material, stockState, quantity);
    }

    /**
     * Creates a stock position with an explicit id (version {@code 0}).
     */
    public static StockPosition of(
            StockPositionId id,
            WarehouseId warehouseId,
            StorageCellId storageCellId,
            MaterialReference material,
            StockState stockState,
            StockQuantity quantity) {
        return rehydrate(id, warehouseId, storageCellId, material, stockState, quantity, 0L);
    }

    /**
     * Rehydrates a persisted stock position. Used by persistence adapters.
     */
    public static StockPosition rehydrate(
            StockPositionId id,
            WarehouseId warehouseId,
            StorageCellId storageCellId,
            MaterialReference material,
            StockState stockState,
            StockQuantity quantity,
            long version) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(warehouseId, "warehouseId");
        Objects.requireNonNull(storageCellId, "storageCellId");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(stockState, "stockState");
        Objects.requireNonNull(quantity, "quantity");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative: " + version);
        }
        return new StockPosition(
                id, warehouseId, storageCellId, material, stockState, quantity, version);
    }

    /**
     * Applies a quantity/state change produced by a warehouse operation. Package-private so that
     * only same-package collaborators ({@link WarehouseOperation}) can mutate stock positions.
     * Preserves technical id and optimistic-lock version until persistence bumps the version.
     */
    StockPosition applyChange(StockState newState, StockQuantity newQuantity) {
        Objects.requireNonNull(newState, "newState");
        Objects.requireNonNull(newQuantity, "newQuantity");
        return new StockPosition(
                id, warehouseId, storageCellId, material, newState, newQuantity, version);
    }

    public StockPositionId id() {
        return id;
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

    public long version() {
        return version;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StockPosition that)) {
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
        return "StockPosition{id="
                + id
                + ", warehouseId="
                + warehouseId
                + ", storageCellId="
                + storageCellId
                + ", material="
                + material
                + ", stockState="
                + stockState
                + ", quantity="
                + quantity
                + ", version="
                + version
                + '}';
    }
}

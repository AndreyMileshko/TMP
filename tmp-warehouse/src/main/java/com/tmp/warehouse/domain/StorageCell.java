package com.tmp.warehouse.domain;

import java.util.Objects;

/**
 * Storage cell belonging to a warehouse (Warehouse Specification §5).
 *
 * <p>A cell always references its owning warehouse. Code is mandatory.
 */
public final class StorageCell {

    private final StorageCellId id;
    private final WarehouseId warehouseId;
    private final String code;
    private final boolean active;

    private StorageCell(StorageCellId id, WarehouseId warehouseId, String code, boolean active) {
        this.id = id;
        this.warehouseId = warehouseId;
        this.code = code;
        this.active = active;
    }

    public static StorageCell create(StorageCellId id, WarehouseId warehouseId, String code) {
        return of(id, warehouseId, code, true);
    }

    public static StorageCell of(
            StorageCellId id, WarehouseId warehouseId, String code, boolean active) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(warehouseId, "warehouseId");
        String normalizedCode = requireNonBlank(code, "code");
        return new StorageCell(id, warehouseId, normalizedCode, active);
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return trimmed;
    }

    public StorageCell deactivate() {
        if (!active) {
            return this;
        }
        return new StorageCell(id, warehouseId, code, false);
    }

    public StorageCell activate() {
        if (active) {
            return this;
        }
        return new StorageCell(id, warehouseId, code, true);
    }

    public boolean belongsTo(WarehouseId warehouse) {
        Objects.requireNonNull(warehouse, "warehouse");
        return warehouseId.equals(warehouse);
    }

    public StorageCellId id() {
        return id;
    }

    public WarehouseId warehouseId() {
        return warehouseId;
    }

    public String code() {
        return code;
    }

    public boolean active() {
        return active;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StorageCell that)) {
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
        return "StorageCell{id=" + id + ", warehouseId=" + warehouseId + ", code=" + code + '}';
    }
}

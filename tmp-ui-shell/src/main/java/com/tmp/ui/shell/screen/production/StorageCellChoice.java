package com.tmp.ui.shell.screen.production;

import com.tmp.warehouse.api.WarehouseApi.StorageCellView;
import java.util.Objects;
import java.util.UUID;

/**
 * ComboBox display item for a storage cell (code, not UUID). Local to Production UI to avoid
 * coupling to Warehouse UI package.
 */
public final class StorageCellChoice {

    private final UUID id;
    private final UUID warehouseId;
    private final String label;
    private final boolean active;

    public StorageCellChoice(UUID id, UUID warehouseId, String label, boolean active) {
        this.id = Objects.requireNonNull(id, "id");
        this.warehouseId = Objects.requireNonNull(warehouseId, "warehouseId");
        this.label = Objects.requireNonNull(label, "label");
        this.active = active;
    }

    public static StorageCellChoice from(StorageCellView view) {
        Objects.requireNonNull(view, "view");
        return new StorageCellChoice(
                view.storageCellId(), view.warehouseId(), view.code(), view.active());
    }

    public UUID id() {
        return id;
    }

    public UUID warehouseId() {
        return warehouseId;
    }

    public String label() {
        return label;
    }

    public boolean active() {
        return active;
    }

    @Override
    public String toString() {
        return label;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StorageCellChoice that)) {
            return false;
        }
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}

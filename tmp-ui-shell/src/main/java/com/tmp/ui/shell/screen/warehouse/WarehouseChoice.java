package com.tmp.ui.shell.screen.warehouse;

import com.tmp.warehouse.api.WarehouseApi.WarehouseView;
import java.util.Objects;
import java.util.UUID;

/**
 * ComboBox display item for a warehouse (code/name, not UUID).
 */
public final class WarehouseChoice {

    private final UUID id;
    private final String label;
    private final boolean active;

    public WarehouseChoice(UUID id, String label, boolean active) {
        this.id = Objects.requireNonNull(id, "id");
        this.label = Objects.requireNonNull(label, "label");
        this.active = active;
    }

    public static WarehouseChoice from(WarehouseView view) {
        Objects.requireNonNull(view, "view");
        String label = view.code() + " — " + view.name();
        return new WarehouseChoice(view.warehouseId(), label, view.active());
    }

    public UUID id() {
        return id;
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
        if (!(other instanceof WarehouseChoice that)) {
            return false;
        }
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}

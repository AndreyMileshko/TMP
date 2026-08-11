package com.tmp.ui.shell.screen.warehouse;

import com.tmp.warehouse.api.MaterialDisplayFormatting;
import com.tmp.warehouse.api.WarehouseApi.MaterialReferenceView;
import java.util.Objects;
import java.util.UUID;

/** ComboBox display item for a warehouse material reference. */
public final class MaterialChoice {

    private final UUID id;
    private final String label;

    public MaterialChoice(UUID id, String label) {
        this.id = Objects.requireNonNull(id, "id");
        this.label = Objects.requireNonNull(label, "label");
    }

    public static MaterialChoice from(MaterialReferenceView view) {
        Objects.requireNonNull(view, "view");
        return new MaterialChoice(view.materialReferenceId(), formatLabel(view));
    }

    static String formatLabel(MaterialReferenceView view) {
        String description =
                MaterialDisplayFormatting.formatDescription(
                        view.name(), view.color(), view.size(), view.unitOfMeasure());
        if (description.isBlank()) {
            return view.article();
        }
        return view.article() + " | " + description;
    }

    public UUID id() {
        return id;
    }

    public String label() {
        return label;
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
        if (!(other instanceof MaterialChoice that)) {
            return false;
        }
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}

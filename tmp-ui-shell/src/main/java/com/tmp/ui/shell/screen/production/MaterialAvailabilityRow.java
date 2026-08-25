package com.tmp.ui.shell.screen.production;

import java.util.Objects;

/** Read-only presentation row for material availability. */
public final class MaterialAvailabilityRow {

    private final String material;
    private final String required;
    private final String mainWarehouse;
    private final String productionWarehouse;
    private final String totalAvailable;
    private final String deficit;
    private final String planningSource;
    private final String statusLabel;
    private final boolean unresolvedOrAmbiguous;

    public MaterialAvailabilityRow(
            String material,
            String required,
            String mainWarehouse,
            String productionWarehouse,
            String totalAvailable,
            String deficit,
            String planningSource,
            String statusLabel,
            boolean unresolvedOrAmbiguous) {
        this.material = Objects.requireNonNull(material, "material");
        this.required = Objects.requireNonNull(required, "required");
        this.mainWarehouse = Objects.requireNonNull(mainWarehouse, "mainWarehouse");
        this.productionWarehouse = Objects.requireNonNull(productionWarehouse, "productionWarehouse");
        this.totalAvailable = Objects.requireNonNull(totalAvailable, "totalAvailable");
        this.deficit = Objects.requireNonNull(deficit, "deficit");
        this.planningSource = Objects.requireNonNull(planningSource, "planningSource");
        this.statusLabel = Objects.requireNonNull(statusLabel, "statusLabel");
        this.unresolvedOrAmbiguous = unresolvedOrAmbiguous;
    }

    public String material() {
        return material;
    }

    public String required() {
        return required;
    }

    public String mainWarehouse() {
        return mainWarehouse;
    }

    public String productionWarehouse() {
        return productionWarehouse;
    }

    public String totalAvailable() {
        return totalAvailable;
    }

    public String deficit() {
        return deficit;
    }

    public String planningSource() {
        return planningSource;
    }

    public String statusLabel() {
        return statusLabel;
    }

    public boolean unresolvedOrAmbiguous() {
        return unresolvedOrAmbiguous;
    }
}

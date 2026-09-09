package com.tmp.ui.shell.screen.production;

import java.util.Objects;
import java.util.UUID;

/**
 * Editable presentation row for a Material Requirement DRAFT line (Stage 3.5.9). No warehouse
 * allocations or recommended/requested dual quantities.
 */
public final class MaterialRequirementLineRow {

    private final UUID lineId;
    private final String materialCode;
    private final String materialName;
    private final String color;
    private final String unitOfMeasure;
    private String quantity;

    public MaterialRequirementLineRow(
            UUID lineId,
            String materialCode,
            String materialName,
            String color,
            String unitOfMeasure,
            String quantity) {
        this.lineId = Objects.requireNonNull(lineId, "lineId");
        this.materialCode = Objects.requireNonNull(materialCode, "materialCode");
        this.materialName = Objects.requireNonNull(materialName, "materialName");
        this.color = Objects.requireNonNull(color, "color");
        this.unitOfMeasure = Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
        this.quantity = quantity == null ? "" : quantity.trim();
    }

    public UUID lineId() {
        return lineId;
    }

    public String materialCode() {
        return materialCode;
    }

    public String materialName() {
        return materialName;
    }

    public String color() {
        return color;
    }

    public String unitOfMeasure() {
        return unitOfMeasure;
    }

    public String quantity() {
        return quantity;
    }

    public void setQuantity(String quantity) {
        this.quantity = quantity == null ? "" : quantity.trim();
    }
}

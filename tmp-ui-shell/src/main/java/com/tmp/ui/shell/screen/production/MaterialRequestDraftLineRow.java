package com.tmp.ui.shell.screen.production;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

/** Editable Material Requirement line row for STEP 2 DRAFT. Identity fields are read-only. */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "JavaFX property accessors intentionally expose mutable observables")
public final class MaterialRequestDraftLineRow {

    private final UUID lineId;
    private final String materialCode;
    private final String materialName;
    private final String color;
    private final String unitOfMeasure;
    private final ObjectProperty<BigDecimal> quantity;

    public MaterialRequestDraftLineRow(
            UUID lineId,
            String materialCode,
            String materialName,
            String color,
            String unitOfMeasure,
            BigDecimal quantity) {
        this.lineId = Objects.requireNonNull(lineId, "lineId");
        this.materialCode = Objects.requireNonNull(materialCode, "materialCode");
        this.materialName = Objects.requireNonNull(materialName, "materialName");
        this.color = color == null ? "" : color;
        this.unitOfMeasure = Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
        this.quantity = new SimpleObjectProperty<>(Objects.requireNonNull(quantity, "quantity"));
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

    public BigDecimal quantity() {
        return quantity.get();
    }

    public ObjectProperty<BigDecimal> quantityProperty() {
        return quantity;
    }

    public void setQuantity(BigDecimal value) {
        quantity.set(Objects.requireNonNull(value, "quantity"));
    }
}

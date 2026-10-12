package com.tmp.ui.shell.screen.production;

import com.tmp.ui.shell.order.DecimalUiFormat;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * Material Requirement line row for STEP 2 DRAFT. Identity and «Требуется» / «На производстве» are
 * read-only; «К запросу» (request quantity) is editable by the master.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "JavaFX property accessors intentionally expose mutable observables")
public final class MaterialRequestDraftLineRow {

    public static final String UNRESOLVED_STOCK_DISPLAY = "—";

    private final UUID lineId;
    private final String materialCode;
    private final String materialName;
    private final String color;
    private final String unitOfMeasure;
    private final BigDecimal requiredQuantity;
    private final ObjectProperty<BigDecimal> quantity;
    /** {@code null} means unmatched/ambiguous MaterialReference — display "—". */
    private final BigDecimal onProductionAvailable;

    public MaterialRequestDraftLineRow(
            UUID lineId,
            String materialCode,
            String materialName,
            String color,
            String unitOfMeasure,
            BigDecimal requiredQuantity,
            BigDecimal quantity,
            BigDecimal onProductionAvailable) {
        this.lineId = Objects.requireNonNull(lineId, "lineId");
        this.materialCode = Objects.requireNonNull(materialCode, "materialCode");
        this.materialName = Objects.requireNonNull(materialName, "materialName");
        this.color = color == null ? "" : color;
        this.unitOfMeasure = Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
        this.requiredQuantity = Objects.requireNonNull(requiredQuantity, "requiredQuantity");
        this.quantity = new SimpleObjectProperty<>(Objects.requireNonNull(quantity, "quantity"));
        this.onProductionAvailable = onProductionAvailable;
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

    public BigDecimal requiredQuantity() {
        return requiredQuantity;
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

    public BigDecimal onProductionAvailable() {
        return onProductionAvailable;
    }

    public String requiredQuantityLabel() {
        return DecimalUiFormat.format(requiredQuantity);
    }

    public String onProductionAvailableLabel() {
        if (onProductionAvailable == null) {
            return UNRESOLVED_STOCK_DISPLAY;
        }
        return DecimalUiFormat.format(onProductionAvailable);
    }

    public String quantityToRequestLabel() {
        return DecimalUiFormat.format(quantity());
    }
}

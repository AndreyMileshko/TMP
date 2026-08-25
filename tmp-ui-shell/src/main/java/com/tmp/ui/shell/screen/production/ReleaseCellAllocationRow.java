package com.tmp.ui.shell.screen.production;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import javafx.collections.ObservableList;

/** One explicit Release production-cell allocation (0..N per material actual usage). */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "JavaFX ComboBox binding requires live ObservableList references.")
public final class ReleaseCellAllocationRow {

    private final UUID sourceOrderItemId;
    private final UUID materialReferenceId;
    private final ObservableList<StorageCellChoice> cellChoices;
    private StorageCellChoice productionCell;
    private String quantity;

    public ReleaseCellAllocationRow(
            UUID sourceOrderItemId,
            UUID materialReferenceId,
            ObservableList<StorageCellChoice> cellChoices) {
        this.sourceOrderItemId = Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
        this.materialReferenceId =
                Objects.requireNonNull(materialReferenceId, "materialReferenceId");
        this.cellChoices = Objects.requireNonNull(cellChoices, "cellChoices");
        this.quantity = "";
    }

    public UUID sourceOrderItemId() {
        return sourceOrderItemId;
    }

    public UUID materialReferenceId() {
        return materialReferenceId;
    }

    public ObservableList<StorageCellChoice> cellChoices() {
        return cellChoices;
    }

    public StorageCellChoice productionCell() {
        return productionCell;
    }

    public void setProductionCell(StorageCellChoice productionCell) {
        this.productionCell = productionCell;
    }

    public String quantity() {
        return quantity;
    }

    public void setQuantity(String quantity) {
        this.quantity = quantity == null ? "" : quantity.trim();
    }

    public BigDecimal parseQuantity() {
        return new BigDecimal(quantity);
    }
}

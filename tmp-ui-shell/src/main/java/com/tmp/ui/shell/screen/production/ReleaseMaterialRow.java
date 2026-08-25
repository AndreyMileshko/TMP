package com.tmp.ui.shell.screen.production;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/** Editable presentation row for release material plan/fact. */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "JavaFX ComboBox binding requires live ObservableList references.")
public final class ReleaseMaterialRow {

    private final UUID sourceOrderItemId;
    private final UUID materialReferenceId;
    private final String materialLabel;
    private final String plannedQuantity;
    private String actualQuantity;
    private final ObservableList<StorageCellChoice> cellChoices =
            FXCollections.observableArrayList();
    private StorageCellChoice productionCell;

    public ReleaseMaterialRow(
            UUID sourceOrderItemId,
            UUID materialReferenceId,
            String materialLabel,
            String plannedQuantity,
            String actualQuantity) {
        this.sourceOrderItemId = Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
        this.materialReferenceId =
                Objects.requireNonNull(materialReferenceId, "materialReferenceId");
        this.materialLabel = Objects.requireNonNull(materialLabel, "materialLabel");
        this.plannedQuantity = Objects.requireNonNull(plannedQuantity, "plannedQuantity");
        this.actualQuantity = Objects.requireNonNull(actualQuantity, "actualQuantity");
    }

    public UUID sourceOrderItemId() {
        return sourceOrderItemId;
    }

    public UUID materialReferenceId() {
        return materialReferenceId;
    }

    public String materialLabel() {
        return materialLabel;
    }

    public String plannedQuantity() {
        return plannedQuantity;
    }

    public String actualQuantity() {
        return actualQuantity;
    }

    public void setActualQuantity(String actualQuantity) {
        this.actualQuantity = actualQuantity == null ? "" : actualQuantity.trim();
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

    public BigDecimal parseActualQuantity() {
        return new BigDecimal(actualQuantity);
    }
}

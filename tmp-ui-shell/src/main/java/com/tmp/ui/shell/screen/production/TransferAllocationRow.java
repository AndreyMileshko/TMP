package com.tmp.ui.shell.screen.production;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import javafx.collections.ObservableList;

/** One explicit Transfer cell allocation for a template line (1..N per included line). */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "JavaFX ComboBox binding requires live ObservableList references.")
public final class TransferAllocationRow {

    private final UUID templateLineId;
    private final ObservableList<StorageCellChoice> sourceCellChoices;
    private final ObservableList<StorageCellChoice> destinationCellChoices;
    private StorageCellChoice sourceCell;
    private StorageCellChoice destinationCell;
    private String quantity;

    public TransferAllocationRow(
            UUID templateLineId,
            ObservableList<StorageCellChoice> sourceCellChoices,
            ObservableList<StorageCellChoice> destinationCellChoices) {
        this.templateLineId = Objects.requireNonNull(templateLineId, "templateLineId");
        this.sourceCellChoices = Objects.requireNonNull(sourceCellChoices, "sourceCellChoices");
        this.destinationCellChoices =
                Objects.requireNonNull(destinationCellChoices, "destinationCellChoices");
        this.quantity = "";
    }

    public UUID templateLineId() {
        return templateLineId;
    }

    public ObservableList<StorageCellChoice> sourceCellChoices() {
        return sourceCellChoices;
    }

    public ObservableList<StorageCellChoice> destinationCellChoices() {
        return destinationCellChoices;
    }

    public StorageCellChoice sourceCell() {
        return sourceCell;
    }

    public void setSourceCell(StorageCellChoice sourceCell) {
        this.sourceCell = sourceCell;
    }

    public StorageCellChoice destinationCell() {
        return destinationCell;
    }

    public void setDestinationCell(StorageCellChoice destinationCell) {
        this.destinationCell = destinationCell;
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

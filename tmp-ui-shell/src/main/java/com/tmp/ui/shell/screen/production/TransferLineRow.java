package com.tmp.ui.shell.screen.production;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/** Editable presentation row for a material transfer template line. */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "JavaFX ComboBox binding requires live ObservableList references.")
public final class TransferLineRow {

    private final UUID lineId;
    private final UUID materialReferenceId;
    private final String materialLabel;
    private final String recommendedQuantity;
    private final String requiredQuantity;
    private String requestedQuantity;
    private boolean included;
    private final ObservableList<StorageCellChoice> sourceCellChoices =
            FXCollections.observableArrayList();
    private final ObservableList<StorageCellChoice> destinationCellChoices =
            FXCollections.observableArrayList();
    private StorageCellChoice sourceCell;
    private StorageCellChoice destinationCell;
    private String allocationQuantity;

    public TransferLineRow(
            UUID lineId,
            UUID materialReferenceId,
            String materialLabel,
            String recommendedQuantity,
            String requiredQuantity,
            String requestedQuantity,
            boolean included) {
        this.lineId = Objects.requireNonNull(lineId, "lineId");
        this.materialReferenceId = Objects.requireNonNull(materialReferenceId, "materialReferenceId");
        this.materialLabel = Objects.requireNonNull(materialLabel, "materialLabel");
        this.recommendedQuantity = Objects.requireNonNull(recommendedQuantity, "recommendedQuantity");
        this.requiredQuantity = Objects.requireNonNull(requiredQuantity, "requiredQuantity");
        this.requestedQuantity = Objects.requireNonNull(requestedQuantity, "requestedQuantity");
        this.included = included;
        this.allocationQuantity = requestedQuantity;
    }

    public UUID lineId() {
        return lineId;
    }

    public UUID materialReferenceId() {
        return materialReferenceId;
    }

    public String materialLabel() {
        return materialLabel;
    }

    public String recommendedQuantity() {
        return recommendedQuantity;
    }

    public String requiredQuantity() {
        return requiredQuantity;
    }

    public String requestedQuantity() {
        return requestedQuantity;
    }

    public void setRequestedQuantity(String requestedQuantity) {
        this.requestedQuantity =
                requestedQuantity == null ? "" : requestedQuantity.trim();
    }

    public boolean included() {
        return included;
    }

    public void setIncluded(boolean included) {
        this.included = included;
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

    public String allocationQuantity() {
        return allocationQuantity;
    }

    public void setAllocationQuantity(String allocationQuantity) {
        this.allocationQuantity =
                allocationQuantity == null ? "" : allocationQuantity.trim();
    }

    public BigDecimal parseAllocationQuantity() {
        return new BigDecimal(allocationQuantity);
    }
}

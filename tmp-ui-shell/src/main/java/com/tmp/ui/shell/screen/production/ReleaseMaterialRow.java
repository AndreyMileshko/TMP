package com.tmp.ui.shell.screen.production;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Editable presentation row for release material plan/fact. Holds 0..N explicit production-cell
 * allocations; cells are never auto-selected by an allocation algorithm.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "JavaFX ComboBox binding requires live ObservableList references.")
public final class ReleaseMaterialRow {

    private final UUID sourceOrderId;
    private final UUID sourceOrderItemId;
    private final UUID materialReferenceId;
    private final String orderNumberLabel;
    private final String materialLabel;
    private final String plannedQuantity;
    private String actualQuantity;
    private final ObservableList<StorageCellChoice> cellChoices =
            FXCollections.observableArrayList();
    private final ObservableList<CellAllocation> allocations =
            FXCollections.observableArrayList();

    public ReleaseMaterialRow(
            UUID sourceOrderId,
            UUID sourceOrderItemId,
            UUID materialReferenceId,
            String orderNumberLabel,
            String materialLabel,
            String plannedQuantity,
            String actualQuantity) {
        this.sourceOrderId = Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        this.sourceOrderItemId = Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
        this.materialReferenceId =
                Objects.requireNonNull(materialReferenceId, "materialReferenceId");
        this.orderNumberLabel = Objects.requireNonNull(orderNumberLabel, "orderNumberLabel");
        this.materialLabel = Objects.requireNonNull(materialLabel, "materialLabel");
        this.plannedQuantity = Objects.requireNonNull(plannedQuantity, "plannedQuantity");
        this.actualQuantity = Objects.requireNonNull(actualQuantity, "actualQuantity");
    }

    public UUID sourceOrderId() {
        return sourceOrderId;
    }

    public UUID sourceOrderItemId() {
        return sourceOrderItemId;
    }

    public UUID materialReferenceId() {
        return materialReferenceId;
    }

    public String orderNumberLabel() {
        return orderNumberLabel;
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

    public ObservableList<CellAllocation> allocations() {
        return allocations;
    }

    public CellAllocation addAllocation() {
        CellAllocation row = new CellAllocation(sourceOrderItemId, materialReferenceId, cellChoices);
        allocations.add(row);
        return row;
    }

    public void removeAllocation(CellAllocation row) {
        Objects.requireNonNull(row, "row");
        allocations.remove(row);
    }

    public void clearAllocations() {
        allocations.clear();
    }

    public BigDecimal parseActualQuantity() {
        return new BigDecimal(actualQuantity);
    }

    public String allocationSummary() {
        if (allocations.isEmpty()) {
            return "нет распределений";
        }
        StringBuilder summary = new StringBuilder();
        for (CellAllocation allocation : allocations) {
            if (summary.length() > 0) {
                summary.append("; ");
            }
            String cell =
                    allocation.productionCell() == null
                            ? "—"
                            : allocation.productionCell().label();
            summary.append(cell).append(": ").append(allocation.quantity());
        }
        return summary.toString();
    }

    /** One explicit Release production-cell allocation. */
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP",
            justification = "JavaFX ComboBox binding requires live ObservableList references.")
    public static final class CellAllocation {
        private final UUID sourceOrderItemId;
        private final UUID materialReferenceId;
        private final ObservableList<StorageCellChoice> cellChoices;
        private StorageCellChoice productionCell;
        private String quantity;

        CellAllocation(
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
}

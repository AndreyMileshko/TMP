package com.tmp.ui.shell.screen.warehouse;

import java.util.Objects;
import java.util.UUID;

/** Presentation row for a Warehouse-owned transfer DRAFT (Production or manual). */
public final class TransferDraftRow {

    private final UUID operationId;
    private final String status;
    private final String materialLabel;
    private final String quantity;
    private final String sourceWarehouseLabel;
    private final String sourceCellLabel;
    private final String destinationWarehouseLabel;
    private final String destinationCellLabel;

    public TransferDraftRow(
            UUID operationId,
            String status,
            String materialLabel,
            String quantity,
            String sourceWarehouseLabel,
            String sourceCellLabel,
            String destinationWarehouseLabel,
            String destinationCellLabel) {
        this.operationId = Objects.requireNonNull(operationId, "operationId");
        this.status = Objects.requireNonNull(status, "status");
        this.materialLabel = Objects.requireNonNull(materialLabel, "materialLabel");
        this.quantity = Objects.requireNonNull(quantity, "quantity");
        this.sourceWarehouseLabel = Objects.requireNonNull(sourceWarehouseLabel, "sourceWarehouseLabel");
        this.sourceCellLabel = Objects.requireNonNull(sourceCellLabel, "sourceCellLabel");
        this.destinationWarehouseLabel =
                Objects.requireNonNull(destinationWarehouseLabel, "destinationWarehouseLabel");
        this.destinationCellLabel =
                Objects.requireNonNull(destinationCellLabel, "destinationCellLabel");
    }

    public UUID operationId() {
        return operationId;
    }

    public String status() {
        return status;
    }

    public String materialLabel() {
        return materialLabel;
    }

    public String quantity() {
        return quantity;
    }

    public String sourceWarehouseLabel() {
        return sourceWarehouseLabel;
    }

    public String sourceCellLabel() {
        return sourceCellLabel;
    }

    public String destinationWarehouseLabel() {
        return destinationWarehouseLabel;
    }

    public String destinationCellLabel() {
        return destinationCellLabel;
    }
}

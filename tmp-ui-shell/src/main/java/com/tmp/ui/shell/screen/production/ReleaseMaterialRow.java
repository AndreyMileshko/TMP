package com.tmp.ui.shell.screen.production;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Editable presentation row for release material plan/fact. Master enters actual consumption only;
 * production-cell allocation is performed server-side during release posting.
 */
public final class ReleaseMaterialRow {

    private final UUID sourceOrderId;
    private final UUID sourceOrderItemId;
    private final UUID materialReferenceId;
    private final String orderNumberLabel;
    private final String materialLabel;
    private final String plannedQuantity;
    private String actualQuantity;

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

    public BigDecimal parseActualQuantity() {
        return new BigDecimal(actualQuantity);
    }
}

package com.tmp.ui.shell.screen.production;

import java.util.Objects;
import java.util.UUID;

/** Read-only presentation row for a production order item. */
public final class ProductionItemRow {

    private final UUID orderItemId;
    private final String positionLabel;
    private final String statusLabel;
    private final String orderedQuantity;
    private final String activeQuantity;
    private final String releasedQuantity;
    private final String specificationId;
    private final String cuttingPlanRefs;
    private final long activeQuantityValue;
    private String releaseQuantityInput;

    public ProductionItemRow(
            UUID orderItemId,
            String positionLabel,
            String statusLabel,
            String orderedQuantity,
            String activeQuantity,
            String releasedQuantity,
            String specificationId,
            String cuttingPlanRefs,
            long activeQuantityValue,
            String releaseQuantityInput) {
        this.orderItemId = Objects.requireNonNull(orderItemId, "orderItemId");
        this.positionLabel = Objects.requireNonNull(positionLabel, "positionLabel");
        this.statusLabel = Objects.requireNonNull(statusLabel, "statusLabel");
        this.orderedQuantity = Objects.requireNonNull(orderedQuantity, "orderedQuantity");
        this.activeQuantity = Objects.requireNonNull(activeQuantity, "activeQuantity");
        this.releasedQuantity = Objects.requireNonNull(releasedQuantity, "releasedQuantity");
        this.specificationId = Objects.requireNonNull(specificationId, "specificationId");
        this.cuttingPlanRefs = Objects.requireNonNull(cuttingPlanRefs, "cuttingPlanRefs");
        this.activeQuantityValue = activeQuantityValue;
        this.releaseQuantityInput =
                releaseQuantityInput == null ? "" : releaseQuantityInput.trim();
    }

    public UUID orderItemId() {
        return orderItemId;
    }

    public String positionLabel() {
        return positionLabel;
    }

    public String statusLabel() {
        return statusLabel;
    }

    public String orderedQuantity() {
        return orderedQuantity;
    }

    public String activeQuantity() {
        return activeQuantity;
    }

    public String releasedQuantity() {
        return releasedQuantity;
    }

    public String specificationId() {
        return specificationId;
    }

    public String cuttingPlanRefs() {
        return cuttingPlanRefs;
    }

    public long activeQuantityValue() {
        return activeQuantityValue;
    }

    public String releaseQuantityInput() {
        return releaseQuantityInput;
    }

    public void setReleaseQuantityInput(String releaseQuantityInput) {
        this.releaseQuantityInput = releaseQuantityInput == null ? "" : releaseQuantityInput.trim();
    }
}

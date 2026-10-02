package com.tmp.ui.shell.screen.production;

import java.util.Objects;
import java.util.UUID;

/** Read-only presentation row for an order position on the Production Order Card. */
public final class ProductionItemRow {

    private final UUID orderItemId;
    private final String positionLabel;
    private final String productLabel;
    private final String quantityLabel;
    private final String statusLabel;
    private final String releasedLabel;
    private final String remainingLabel;

    public ProductionItemRow(
            UUID orderItemId,
            String positionLabel,
            String productLabel,
            String quantityLabel,
            String statusLabel,
            String releasedLabel,
            String remainingLabel) {
        this.orderItemId = Objects.requireNonNull(orderItemId, "orderItemId");
        this.positionLabel = Objects.requireNonNull(positionLabel, "positionLabel");
        this.productLabel = Objects.requireNonNull(productLabel, "productLabel");
        this.quantityLabel = Objects.requireNonNull(quantityLabel, "quantityLabel");
        this.statusLabel = Objects.requireNonNull(statusLabel, "statusLabel");
        this.releasedLabel = Objects.requireNonNull(releasedLabel, "releasedLabel");
        this.remainingLabel = Objects.requireNonNull(remainingLabel, "remainingLabel");
    }

    public UUID orderItemId() {
        return orderItemId;
    }

    public String positionLabel() {
        return positionLabel;
    }

    public String productLabel() {
        return productLabel;
    }

    public String quantityLabel() {
        return quantityLabel;
    }

    public String statusLabel() {
        return statusLabel;
    }

    public String releasedLabel() {
        return releasedLabel;
    }

    public String remainingLabel() {
        return remainingLabel;
    }
}

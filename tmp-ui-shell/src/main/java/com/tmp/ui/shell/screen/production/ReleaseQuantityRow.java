package com.tmp.ui.shell.screen.production;

import com.tmp.production.api.ProductionApplicationApi.QuantityModeView;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Objects;
import java.util.UUID;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleLongProperty;

/**
 * STEP 1 presentation row for cross-order Release. Quantity Mode comes from the order; STANDARD
 * release quantity is fixed to the active remainder.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "JavaFX property accessors intentionally expose mutable observables")
public final class ReleaseQuantityRow {

    private final UUID sourceOrderId;
    private final UUID sourceOrderItemId;
    private final String orderNumberLabel;
    private final String positionLabel;
    private final String productLabel;
    private final QuantityModeView quantityMode;
    private final long orderedQuantity;
    private final long releasedQuantity;
    private final long activeProductionQuantity;
    private final LongProperty releaseQuantity;

    public ReleaseQuantityRow(
            UUID sourceOrderId,
            UUID sourceOrderItemId,
            String orderNumberLabel,
            String positionLabel,
            String productLabel,
            QuantityModeView quantityMode,
            long orderedQuantity,
            long releasedQuantity,
            long activeProductionQuantity,
            long defaultReleaseQuantity) {
        this.sourceOrderId = Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        this.sourceOrderItemId = Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
        this.orderNumberLabel = Objects.requireNonNull(orderNumberLabel, "orderNumberLabel");
        this.positionLabel = Objects.requireNonNull(positionLabel, "positionLabel");
        this.productLabel = Objects.requireNonNull(productLabel, "productLabel");
        this.quantityMode = Objects.requireNonNull(quantityMode, "quantityMode");
        this.orderedQuantity = orderedQuantity;
        this.releasedQuantity = releasedQuantity;
        this.activeProductionQuantity = activeProductionQuantity;
        this.releaseQuantity = new SimpleLongProperty(defaultReleaseQuantity);
    }

    public UUID sourceOrderId() {
        return sourceOrderId;
    }

    public UUID sourceOrderItemId() {
        return sourceOrderItemId;
    }

    public String orderNumberLabel() {
        return orderNumberLabel;
    }

    public String positionLabel() {
        return positionLabel;
    }

    public String productLabel() {
        return productLabel;
    }

    public QuantityModeView quantityMode() {
        return quantityMode;
    }

    public boolean standardMode() {
        return quantityMode == QuantityModeView.STANDARD;
    }

    public long orderedQuantity() {
        return orderedQuantity;
    }

    public long releasedQuantity() {
        return releasedQuantity;
    }

    public long activeProductionQuantity() {
        return activeProductionQuantity;
    }

    public long releaseQuantity() {
        return releaseQuantity.get();
    }

    public LongProperty releaseQuantityProperty() {
        return releaseQuantity;
    }

    public void setReleaseQuantity(long value) {
        releaseQuantity.set(value);
    }

    public String modeLabel() {
        return ProductionPresentationLabels.quantityMode(quantityMode);
    }
}

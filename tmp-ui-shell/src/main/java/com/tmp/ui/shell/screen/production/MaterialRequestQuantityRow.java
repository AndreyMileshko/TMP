package com.tmp.ui.shell.screen.production;

import com.tmp.production.api.ProductionApplicationApi.QuantityModeView;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Objects;
import java.util.UUID;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleLongProperty;

/**
 * STEP 1 presentation row for cross-order Material Request. Quantity Mode and requestable come from
 * Production APIs; UI does not calculate material norms.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "JavaFX property accessors intentionally expose mutable observables")
public final class MaterialRequestQuantityRow {

    private final UUID sourceOrderId;
    private final UUID sourceOrderItemId;
    private final String orderNumberLabel;
    private final String positionLabel;
    private final String productLabel;
    private final QuantityModeView quantityMode;
    private final long requestableProductQuantity;
    private final LongProperty requestedProductQuantity;

    public MaterialRequestQuantityRow(
            UUID sourceOrderId,
            UUID sourceOrderItemId,
            String orderNumberLabel,
            String positionLabel,
            String productLabel,
            QuantityModeView quantityMode,
            long requestableProductQuantity,
            long defaultRequestedProductQuantity) {
        this.sourceOrderId = Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        this.sourceOrderItemId = Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
        this.orderNumberLabel = Objects.requireNonNull(orderNumberLabel, "orderNumberLabel");
        this.positionLabel = Objects.requireNonNull(positionLabel, "positionLabel");
        this.productLabel = Objects.requireNonNull(productLabel, "productLabel");
        this.quantityMode = Objects.requireNonNull(quantityMode, "quantityMode");
        this.requestableProductQuantity = requestableProductQuantity;
        this.requestedProductQuantity =
                new SimpleLongProperty(defaultRequestedProductQuantity);
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

    public long requestableProductQuantity() {
        return requestableProductQuantity;
    }

    public long requestedProductQuantity() {
        return requestedProductQuantity.get();
    }

    public LongProperty requestedProductQuantityProperty() {
        return requestedProductQuantity;
    }

    public void setRequestedProductQuantity(long value) {
        requestedProductQuantity.set(value);
    }

    public String modeLabel() {
        return ProductionPresentationLabels.quantityMode(quantityMode);
    }
}

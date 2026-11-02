package com.tmp.ui.shell.screen.production;

import com.tmp.production.api.ProductionApplicationApi.QuantityModeView;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Objects;
import java.util.UUID;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;

/**
 * STEP 1 presentation row for cross-order Material Request. Quantity Mode, requestable and ordered
 * come from Production APIs; UI does not calculate material norms.
 *
 * <p>Per-position {@link #repeatedRequestProperty()} selects REPEATED vs COUNTED prepare semantics.
 * Default is unchecked (normal counted request).
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
    private final long orderedQuantity;
    private final LongProperty requestedProductQuantity;
    private final BooleanProperty repeatedRequest;

    public MaterialRequestQuantityRow(
            UUID sourceOrderId,
            UUID sourceOrderItemId,
            String orderNumberLabel,
            String positionLabel,
            String productLabel,
            QuantityModeView quantityMode,
            long requestableProductQuantity,
            long defaultRequestedProductQuantity) {
        this(
                sourceOrderId,
                sourceOrderItemId,
                orderNumberLabel,
                positionLabel,
                productLabel,
                quantityMode,
                requestableProductQuantity,
                requestableProductQuantity,
                defaultRequestedProductQuantity);
    }

    public MaterialRequestQuantityRow(
            UUID sourceOrderId,
            UUID sourceOrderItemId,
            String orderNumberLabel,
            String positionLabel,
            String productLabel,
            QuantityModeView quantityMode,
            long requestableProductQuantity,
            long orderedQuantity,
            long defaultRequestedProductQuantity) {
        this.sourceOrderId = Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        this.sourceOrderItemId = Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
        this.orderNumberLabel = Objects.requireNonNull(orderNumberLabel, "orderNumberLabel");
        this.positionLabel = Objects.requireNonNull(positionLabel, "positionLabel");
        this.productLabel = Objects.requireNonNull(productLabel, "productLabel");
        this.quantityMode = Objects.requireNonNull(quantityMode, "quantityMode");
        this.requestableProductQuantity = requestableProductQuantity;
        this.orderedQuantity = orderedQuantity;
        this.requestedProductQuantity =
                new SimpleLongProperty(defaultRequestedProductQuantity);
        this.repeatedRequest = new SimpleBooleanProperty(false);
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

    public long orderedQuantity() {
        return orderedQuantity;
    }

    /**
     * Authoritative maximum for the current request kind: ordered quantity when repeated; normal
     * requestable otherwise.
     */
    public long maximumProductQuantity() {
        return repeatedRequest.get() ? orderedQuantity : requestableProductQuantity;
    }

    /** True when the quantity field is user-editable (FLEXIBLE always; STANDARD only when repeated). */
    public boolean quantityEditable() {
        return repeatedRequest.get() || !standardMode();
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

    public boolean repeatedRequest() {
        return repeatedRequest.get();
    }

    public BooleanProperty repeatedRequestProperty() {
        return repeatedRequest;
    }

    /**
     * Toggles repeated-request mode and clamps/defaults the visible quantity to the new range.
     * Newly enabled repeated prefers {@code 1} unless the current value already fits {@code
     * 1..orderedQuantity}. Switching back to normal restores STANDARD/FLEXIBLE defaults.
     */
    public void setRepeatedRequest(boolean repeated) {
        if (repeatedRequest.get() == repeated) {
            return;
        }
        repeatedRequest.set(repeated);
        applyQuantityAfterKindChange();
    }

    private void applyQuantityAfterKindChange() {
        if (repeatedRequest.get()) {
            long current = requestedProductQuantity.get();
            long max = orderedQuantity;
            if (max <= 0L) {
                requestedProductQuantity.set(0L);
                return;
            }
            if (current >= 1L && current <= max) {
                return;
            }
            requestedProductQuantity.set(1L);
            return;
        }
        if (standardMode()) {
            requestedProductQuantity.set(requestableProductQuantity);
            return;
        }
        long current = requestedProductQuantity.get();
        long max = requestableProductQuantity;
        if (max <= 0L) {
            requestedProductQuantity.set(0L);
            return;
        }
        if (current < 1L || current > max) {
            requestedProductQuantity.set(max);
        }
    }

    public String modeLabel() {
        return ProductionPresentationLabels.quantityMode(quantityMode);
    }
}

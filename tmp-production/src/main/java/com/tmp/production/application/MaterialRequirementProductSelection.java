package com.tmp.production.application;

import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * One Order Item selection for preparing a cross-order Material Requirement.
 *
 * <p>For {@code STANDARD} mode, {@link #requestedProductQuantity()} must be empty — the backend
 * computes the full requestable product quantity. For {@code FLEXIBLE} mode, an explicit positive
 * quantity ≤ requestable is required.
 */
public final class MaterialRequirementProductSelection {

    private final SourceOrderId sourceOrderId;
    private final SourceOrderItemId sourceOrderItemId;
    private final OptionalLong requestedProductQuantity;

    private MaterialRequirementProductSelection(
            SourceOrderId sourceOrderId,
            SourceOrderItemId sourceOrderItemId,
            OptionalLong requestedProductQuantity) {
        this.sourceOrderId = Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        this.sourceOrderItemId = Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
        this.requestedProductQuantity =
                Objects.requireNonNull(requestedProductQuantity, "requestedProductQuantity");
    }

    /** STANDARD (or mode-resolved) selection without an explicit product quantity. */
    public static MaterialRequirementProductSelection of(
            SourceOrderId sourceOrderId, SourceOrderItemId sourceOrderItemId) {
        return new MaterialRequirementProductSelection(
                sourceOrderId, sourceOrderItemId, OptionalLong.empty());
    }

    /** FLEXIBLE selection with an explicit product quantity. */
    public static MaterialRequirementProductSelection of(
            SourceOrderId sourceOrderId,
            SourceOrderItemId sourceOrderItemId,
            long requestedProductQuantity) {
        return new MaterialRequirementProductSelection(
                sourceOrderId, sourceOrderItemId, OptionalLong.of(requestedProductQuantity));
    }

    public SourceOrderId sourceOrderId() {
        return sourceOrderId;
    }

    public SourceOrderItemId sourceOrderItemId() {
        return sourceOrderItemId;
    }

    public OptionalLong requestedProductQuantity() {
        return requestedProductQuantity;
    }
}

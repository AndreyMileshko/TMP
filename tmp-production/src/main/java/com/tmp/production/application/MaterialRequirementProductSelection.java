package com.tmp.production.application;

import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * One Order Item selection for preparing a cross-order Material Requirement.
 *
 * <p>For counted {@code STANDARD} mode, {@link #requestedProductQuantity()} must be empty — the
 * backend computes the full requestable product quantity. For counted {@code FLEXIBLE} mode, an
 * explicit positive quantity ≤ requestable is required.
 *
 * <p>For a repeated request, an explicit positive quantity ≤ orderedQuantity is required regardless
 * of Quantity Mode (including STANDARD).
 */
public final class MaterialRequirementProductSelection {

    private final SourceOrderId sourceOrderId;
    private final SourceOrderItemId sourceOrderItemId;
    private final OptionalLong requestedProductQuantity;
    private final boolean repeatedRequest;

    private MaterialRequirementProductSelection(
            SourceOrderId sourceOrderId,
            SourceOrderItemId sourceOrderItemId,
            OptionalLong requestedProductQuantity,
            boolean repeatedRequest) {
        this.sourceOrderId = Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        this.sourceOrderItemId = Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
        this.requestedProductQuantity =
                Objects.requireNonNull(requestedProductQuantity, "requestedProductQuantity");
        this.repeatedRequest = repeatedRequest;
    }

    /** STANDARD (or mode-resolved) counted selection without an explicit product quantity. */
    public static MaterialRequirementProductSelection of(
            SourceOrderId sourceOrderId, SourceOrderItemId sourceOrderItemId) {
        return new MaterialRequirementProductSelection(
                sourceOrderId, sourceOrderItemId, OptionalLong.empty(), false);
    }

    /** FLEXIBLE counted selection with an explicit product quantity. */
    public static MaterialRequirementProductSelection of(
            SourceOrderId sourceOrderId,
            SourceOrderItemId sourceOrderItemId,
            long requestedProductQuantity) {
        return new MaterialRequirementProductSelection(
                sourceOrderId,
                sourceOrderItemId,
                OptionalLong.of(requestedProductQuantity),
                false);
    }

    /**
     * Repeated additional request with an explicit product quantity (required for STANDARD and
     * FLEXIBLE).
     */
    public static MaterialRequirementProductSelection repeated(
            SourceOrderId sourceOrderId,
            SourceOrderItemId sourceOrderItemId,
            long requestedProductQuantity) {
        return new MaterialRequirementProductSelection(
                sourceOrderId,
                sourceOrderItemId,
                OptionalLong.of(requestedProductQuantity),
                true);
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

    public boolean repeatedRequest() {
        return repeatedRequest;
    }
}

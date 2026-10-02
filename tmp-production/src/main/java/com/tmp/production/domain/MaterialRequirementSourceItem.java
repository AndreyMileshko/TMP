package com.tmp.production.domain;

import java.util.Objects;

/**
 * One Production Order Item contribution of product units covered by a Material Requirement.
 *
 * <p>{@code requestedProductQuantity} is the number of products (not material quantity) included in
 * this requirement for the given order item.
 */
public final class MaterialRequirementSourceItem {

    private final SourceOrderId sourceOrderId;
    private final SourceOrderItemId sourceOrderItemId;
    private final long requestedProductQuantity;

    private MaterialRequirementSourceItem(
            SourceOrderId sourceOrderId,
            SourceOrderItemId sourceOrderItemId,
            long requestedProductQuantity) {
        this.sourceOrderId = Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        this.sourceOrderItemId = Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
        if (requestedProductQuantity <= 0L) {
            throw new IllegalArgumentException(
                    "requestedProductQuantity must be > 0: " + requestedProductQuantity);
        }
        this.requestedProductQuantity = requestedProductQuantity;
    }

    public static MaterialRequirementSourceItem of(
            SourceOrderId sourceOrderId,
            SourceOrderItemId sourceOrderItemId,
            long requestedProductQuantity) {
        return new MaterialRequirementSourceItem(
                sourceOrderId, sourceOrderItemId, requestedProductQuantity);
    }

    public SourceOrderId sourceOrderId() {
        return sourceOrderId;
    }

    public SourceOrderItemId sourceOrderItemId() {
        return sourceOrderItemId;
    }

    public long requestedProductQuantity() {
        return requestedProductQuantity;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MaterialRequirementSourceItem that)) {
            return false;
        }
        return requestedProductQuantity == that.requestedProductQuantity
                && sourceOrderId.equals(that.sourceOrderId)
                && sourceOrderItemId.equals(that.sourceOrderItemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceOrderId, sourceOrderItemId, requestedProductQuantity);
    }

    @Override
    public String toString() {
        return "MaterialRequirementSourceItem{"
                + sourceOrderId
                + "/"
                + sourceOrderItemId
                + " x"
                + requestedProductQuantity
                + "}";
    }
}

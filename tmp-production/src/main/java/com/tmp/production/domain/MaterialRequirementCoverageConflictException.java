package com.tmp.production.domain;

import java.util.Objects;

/**
 * Raised when Submit (or prepare) would exceed requestable product coverage for an order item.
 */
public final class MaterialRequirementCoverageConflictException extends RuntimeException {

    private final SourceOrderId sourceOrderId;
    private final SourceOrderItemId sourceOrderItemId;
    private final long requestedProductQuantity;
    private final long requestableProductQuantity;

    public MaterialRequirementCoverageConflictException(
            SourceOrderId sourceOrderId,
            SourceOrderItemId sourceOrderItemId,
            long requestedProductQuantity,
            long requestableProductQuantity) {
        super(
                "Material requirement product coverage conflict: order="
                        + sourceOrderId
                        + ", item="
                        + sourceOrderItemId
                        + ", requested="
                        + requestedProductQuantity
                        + ", requestable="
                        + requestableProductQuantity);
        this.sourceOrderId = Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        this.sourceOrderItemId = Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
        this.requestedProductQuantity = requestedProductQuantity;
        this.requestableProductQuantity = requestableProductQuantity;
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

    public long requestableProductQuantity() {
        return requestableProductQuantity;
    }
}

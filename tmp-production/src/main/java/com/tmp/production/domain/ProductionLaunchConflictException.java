package com.tmp.production.domain;

/**
 * Raised when whole-order Production Launch conflicts with existing production state.
 */
public final class ProductionLaunchConflictException extends RuntimeException {

    private final SourceOrderId sourceOrderId;
    private final SourceOrderItemId sourceOrderItemId;
    private final SpecificationId specificationId;

    public ProductionLaunchConflictException(
            SourceOrderId sourceOrderId,
            SourceOrderItemId sourceOrderItemId,
            SpecificationId specificationId) {
        super(
                "Production launch conflict for order "
                        + sourceOrderId
                        + ", item "
                        + sourceOrderItemId
                        + ", specification "
                        + specificationId);
        this.sourceOrderId = sourceOrderId;
        this.sourceOrderItemId = sourceOrderItemId;
        this.specificationId = specificationId;
    }

    public SourceOrderId sourceOrderId() {
        return sourceOrderId;
    }

    public SourceOrderItemId sourceOrderItemId() {
        return sourceOrderItemId;
    }

    public SpecificationId specificationId() {
        return specificationId;
    }
}

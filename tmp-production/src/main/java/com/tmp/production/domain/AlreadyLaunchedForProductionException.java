package com.tmp.production.domain;

/**
 * Raised when a duplicate Production Launch is attempted for the same order item
 * and specification combination that already has persisted state.
 */
public final class AlreadyLaunchedForProductionException extends RuntimeException {

    private final SourceOrderItemId sourceOrderItemId;
    private final SpecificationId specificationId;

    public AlreadyLaunchedForProductionException(
            SourceOrderItemId sourceOrderItemId, SpecificationId specificationId) {
        super(
                "Production already launched for order item "
                        + sourceOrderItemId
                        + " with specification "
                        + specificationId);
        this.sourceOrderItemId = sourceOrderItemId;
        this.specificationId = specificationId;
    }

    public SourceOrderItemId sourceOrderItemId() {
        return sourceOrderItemId;
    }

    public SpecificationId specificationId() {
        return specificationId;
    }
}

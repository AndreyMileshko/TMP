package com.tmp.production.domain;

/**
 * Raised when a frozen {@link SpecificationId} cannot be resolved via Order Management Public Query.
 */
public final class FrozenSpecificationUnavailableException extends RuntimeException {

    private final SpecificationId specificationId;

    public FrozenSpecificationUnavailableException(SpecificationId specificationId) {
        super("Frozen specification unavailable: " + specificationId);
        this.specificationId = specificationId;
    }

    public SpecificationId specificationId() {
        return specificationId;
    }
}

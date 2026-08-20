package com.tmp.production.domain;

/**
 * Thrown when Launch cannot resolve a current immutable specification from Order Management.
 */
public final class SpecificationNotAvailableForLaunchException extends RuntimeException {

    private final SourceOrderItemId sourceOrderItemId;

    public SpecificationNotAvailableForLaunchException(SourceOrderItemId sourceOrderItemId) {
        super("No current immutable specification available for launch: " + sourceOrderItemId);
        this.sourceOrderItemId = sourceOrderItemId;
    }

    public SourceOrderItemId sourceOrderItemId() {
        return sourceOrderItemId;
    }
}

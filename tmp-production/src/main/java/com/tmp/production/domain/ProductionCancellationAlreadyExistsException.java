package com.tmp.production.domain;

/** Raised when whole-order Production Cancellation was already posted for the order. */
public final class ProductionCancellationAlreadyExistsException extends RuntimeException {

    private final SourceOrderId sourceOrderId;

    public ProductionCancellationAlreadyExistsException(SourceOrderId sourceOrderId) {
        super("Production Cancellation already posted for order " + sourceOrderId.value());
        this.sourceOrderId = sourceOrderId;
    }

    public SourceOrderId sourceOrderId() {
        return sourceOrderId;
    }
}

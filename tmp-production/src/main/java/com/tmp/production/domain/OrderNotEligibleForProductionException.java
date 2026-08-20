package com.tmp.production.domain;

/**
 * Raised when a customer order cannot be accepted into production.
 */
public final class OrderNotEligibleForProductionException extends RuntimeException {

    private final SourceOrderId sourceOrderId;
    private final String reason;

    public OrderNotEligibleForProductionException(SourceOrderId sourceOrderId, Object status) {
        this(sourceOrderId, "order status " + status);
    }

    public OrderNotEligibleForProductionException(SourceOrderId sourceOrderId, String reason) {
        super("Order " + sourceOrderId + " is not eligible for production: " + reason);
        this.sourceOrderId = sourceOrderId;
        this.reason = reason;
    }

    public SourceOrderId sourceOrderId() {
        return sourceOrderId;
    }

    public String reason() {
        return reason;
    }
}

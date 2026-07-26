package com.tmp.order.application.order;

import com.tmp.order.api.OrderId;
import java.util.Objects;

/**
 * Thrown when {@code approveOrder} preconditions fail (Specification §8.2): at least one
 * {@code ACTIVE} order item is required. Does not involve Production Status.
 */
public final class OrderApprovalRejectedException extends RuntimeException {

    private final OrderId orderId;

    public OrderApprovalRejectedException(OrderId orderId, String reason) {
        super(Objects.requireNonNull(reason, "reason")
                + ": "
                + Objects.requireNonNull(orderId, "orderId"));
        this.orderId = orderId;
    }

    public OrderId orderId() {
        return orderId;
    }
}

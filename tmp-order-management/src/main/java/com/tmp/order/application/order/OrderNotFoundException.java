package com.tmp.order.application.order;

import com.tmp.order.api.OrderId;
import java.util.Objects;

/**
 * Thrown when an order addressed by a document payload cannot be found.
 */
public final class OrderNotFoundException extends RuntimeException {

    private final OrderId orderId;

    public OrderNotFoundException(OrderId orderId) {
        super("Customer order not found: " + Objects.requireNonNull(orderId, "orderId"));
        this.orderId = orderId;
    }

    public OrderId orderId() {
        return orderId;
    }
}

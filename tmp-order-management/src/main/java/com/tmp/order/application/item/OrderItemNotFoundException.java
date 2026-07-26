package com.tmp.order.application.item;

import com.tmp.order.api.OrderItemId;
import java.util.Objects;

/**
 * Thrown when an order item addressed by a document payload cannot be found.
 */
public final class OrderItemNotFoundException extends RuntimeException {

    private final OrderItemId orderItemId;

    public OrderItemNotFoundException(OrderItemId orderItemId) {
        super("Order item not found: " + Objects.requireNonNull(orderItemId, "orderItemId"));
        this.orderItemId = orderItemId;
    }

    public OrderItemId orderItemId() {
        return orderItemId;
    }
}

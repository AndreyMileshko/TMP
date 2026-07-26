package com.tmp.order.application.order;

import com.tmp.order.domain.OrderNumber;
import java.util.Objects;

/**
 * Thrown when {@code createOrder} rejects a duplicate business order number (Specification §8.2).
 */
public final class DuplicateOrderNumberException extends RuntimeException {

    private final OrderNumber orderNumber;

    public DuplicateOrderNumberException(OrderNumber orderNumber) {
        super("Order number already exists: " + Objects.requireNonNull(orderNumber, "orderNumber"));
        this.orderNumber = orderNumber;
    }

    public OrderNumber orderNumber() {
        return orderNumber;
    }
}

package com.tmp.order.application.order;

import com.tmp.order.api.OrderId;
import java.util.Objects;

/**
 * Internal application command to cancel a Draft customer order (Specification §15.2).
 */
public final class CancelOrderCommand {

    private final OrderId orderId;

    public CancelOrderCommand(OrderId orderId) {
        this.orderId = Objects.requireNonNull(orderId, "orderId");
    }

    public OrderId orderId() {
        return orderId;
    }
}

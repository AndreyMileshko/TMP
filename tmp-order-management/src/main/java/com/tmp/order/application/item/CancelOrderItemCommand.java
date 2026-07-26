package com.tmp.order.application.item;

import com.tmp.order.api.OrderItemId;
import java.util.Objects;

/**
 * Internal command to cancel a Draft order item (Specification §15.2).
 */
public final class CancelOrderItemCommand {

    private final OrderItemId orderItemId;

    public CancelOrderItemCommand(OrderItemId orderItemId) {
        this.orderItemId = Objects.requireNonNull(orderItemId, "orderItemId");
    }

    public OrderItemId orderItemId() {
        return orderItemId;
    }
}

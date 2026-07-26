package com.tmp.order.application.order;

import com.tmp.order.api.OrderId;
import java.util.Objects;

/**
 * Internal application command to approve a Draft customer order (Specification §15.2).
 */
public final class ApproveOrderCommand {

    private final OrderId orderId;

    public ApproveOrderCommand(OrderId orderId) {
        this.orderId = Objects.requireNonNull(orderId, "orderId");
    }

    public OrderId orderId() {
        return orderId;
    }
}

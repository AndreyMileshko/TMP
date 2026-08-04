package com.tmp.order.application.order;

import com.tmp.order.api.OrderId;
import java.util.Objects;

/**
 * Command to activate an APPROVED customer order ({@code APPROVED → ACTIVE}, ADR-031).
 */
public final class ActivateOrderCommand {

    private final OrderId orderId;

    public ActivateOrderCommand(OrderId orderId) {
        this.orderId = Objects.requireNonNull(orderId, "orderId");
    }

    public OrderId orderId() {
        return orderId;
    }
}

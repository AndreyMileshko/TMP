package com.tmp.order.application.order;

import com.tmp.order.api.OrderId;
import com.tmp.order.domain.OrderCommercialData;
import java.util.Objects;

/**
 * Internal application command to update commercial fields of a Draft customer order
 * (Specification §15.2).
 */
public final class UpdateOrderCommand {

    private final OrderId orderId;
    private final OrderCommercialData commercialData;

    public UpdateOrderCommand(OrderId orderId, OrderCommercialData commercialData) {
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.commercialData = Objects.requireNonNull(commercialData, "commercialData");
    }

    public OrderId orderId() {
        return orderId;
    }

    public OrderCommercialData commercialData() {
        return commercialData;
    }
}

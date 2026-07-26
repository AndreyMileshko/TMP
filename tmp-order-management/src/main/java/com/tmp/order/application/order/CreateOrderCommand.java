package com.tmp.order.application.order;

import com.tmp.order.domain.OrderCommercialData;
import com.tmp.order.domain.OrderNumber;
import java.util.Objects;

/**
 * Internal application command to create a Draft customer order (Specification §15.2).
 *
 * <p>Invoked only by {@code ORDER_CREATE} document processor; not part of the public API.
 */
public final class CreateOrderCommand {

    private final OrderNumber orderNumber;
    private final OrderCommercialData commercialData;

    public CreateOrderCommand(OrderNumber orderNumber, OrderCommercialData commercialData) {
        this.orderNumber = Objects.requireNonNull(orderNumber, "orderNumber");
        this.commercialData = Objects.requireNonNull(commercialData, "commercialData");
    }

    public OrderNumber orderNumber() {
        return orderNumber;
    }

    public OrderCommercialData commercialData() {
        return commercialData;
    }
}

package com.tmp.order.application.item;

import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.domain.ItemCommercialData;
import com.tmp.order.domain.OrderedQuantity;
import java.util.Objects;

/**
 * Internal command to create a Draft order item with Revision 1 (Specification §15.2).
 */
public final class CreateOrderItemCommand {

    private final OrderId orderId;
    private final OrderItemId orderItemId;
    private final ItemCommercialData commercialData;
    private final OrderedQuantity orderedQuantity;

    public CreateOrderItemCommand(
            OrderId orderId,
            OrderItemId orderItemId,
            ItemCommercialData commercialData,
            OrderedQuantity orderedQuantity) {
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.orderItemId = Objects.requireNonNull(orderItemId, "orderItemId");
        this.commercialData = Objects.requireNonNull(commercialData, "commercialData");
        this.orderedQuantity = Objects.requireNonNull(orderedQuantity, "orderedQuantity");
    }

    public OrderId orderId() {
        return orderId;
    }

    public OrderItemId orderItemId() {
        return orderItemId;
    }

    public ItemCommercialData commercialData() {
        return commercialData;
    }

    public OrderedQuantity orderedQuantity() {
        return orderedQuantity;
    }
}

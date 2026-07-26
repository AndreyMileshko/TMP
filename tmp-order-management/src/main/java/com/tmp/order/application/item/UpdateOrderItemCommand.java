package com.tmp.order.application.item;

import com.tmp.order.api.OrderItemId;
import com.tmp.order.domain.ItemCommercialData;
import java.util.Objects;

/**
 * Internal command to update commercial fields of a Draft order item (Specification §15.2).
 */
public final class UpdateOrderItemCommand {

    private final OrderItemId orderItemId;
    private final ItemCommercialData commercialData;

    public UpdateOrderItemCommand(OrderItemId orderItemId, ItemCommercialData commercialData) {
        this.orderItemId = Objects.requireNonNull(orderItemId, "orderItemId");
        this.commercialData = Objects.requireNonNull(commercialData, "commercialData");
    }

    public OrderItemId orderItemId() {
        return orderItemId;
    }

    public ItemCommercialData commercialData() {
        return commercialData;
    }
}

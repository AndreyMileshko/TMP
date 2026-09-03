package com.tmp.ui.shell.screen.orderitemlist;

import com.tmp.order.api.OrderItemDto;
import com.tmp.order.api.OrderItemId;
import com.tmp.ui.shell.order.worklist.OrderItemOperationalStatus;
import java.util.Objects;

/** Presentation row for the Order Item list (commercial DTO + operational status + quantity). */
public final class OrderItemListRow {

    private final OrderItemDto item;
    private final String quantityDisplay;
    private final OrderItemOperationalStatus operationalStatus;

    public OrderItemListRow(
            OrderItemDto item, String quantityDisplay, OrderItemOperationalStatus operationalStatus) {
        this.item = Objects.requireNonNull(item, "item");
        this.quantityDisplay = quantityDisplay == null ? "" : quantityDisplay;
        this.operationalStatus = Objects.requireNonNull(operationalStatus, "operationalStatus");
    }

    public OrderItemDto item() {
        return item;
    }

    public OrderItemId orderItemId() {
        return item.orderItemId();
    }

    public String productCode() {
        return item.productCode() == null ? "" : item.productCode();
    }

    public String name() {
        return item.name() == null ? "" : item.name();
    }

    public String quantityDisplay() {
        return quantityDisplay;
    }

    public OrderItemOperationalStatus operationalStatus() {
        return operationalStatus;
    }
}

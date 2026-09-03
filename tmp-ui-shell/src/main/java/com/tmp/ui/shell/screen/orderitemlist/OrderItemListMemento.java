package com.tmp.ui.shell.screen.orderitemlist;

import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemId;
import java.util.Objects;

/**
 * Session-only Order item list memento for Shell Back/Forward. Not persisted between logins.
 */
public final class OrderItemListMemento {

    private final OrderId orderId;
    private final int pageIndex;
    private final OrderItemId selectedItemId;

    public OrderItemListMemento(OrderId orderId, int pageIndex, OrderItemId selectedItemId) {
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.pageIndex = pageIndex;
        this.selectedItemId = selectedItemId;
    }

    public OrderId orderId() {
        return orderId;
    }

    public int pageIndex() {
        return pageIndex;
    }

    public OrderItemId selectedItemId() {
        return selectedItemId;
    }
}

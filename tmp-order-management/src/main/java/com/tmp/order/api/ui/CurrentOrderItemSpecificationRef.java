package com.tmp.order.api.ui;

import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.RevisionNumber;
import java.util.Objects;

/**
 * User-facing reference to the current item specification revision (hides draft vs active choice).
 */
public final class CurrentOrderItemSpecificationRef {

    private final OrderItemId orderItemId;
    private final RevisionNumber revisionNumber;

    private CurrentOrderItemSpecificationRef(
            OrderItemId orderItemId, RevisionNumber revisionNumber) {
        this.orderItemId = orderItemId;
        this.revisionNumber = revisionNumber;
    }

    public static CurrentOrderItemSpecificationRef of(
            OrderItemId orderItemId, RevisionNumber revisionNumber) {
        return new CurrentOrderItemSpecificationRef(
                Objects.requireNonNull(orderItemId, "orderItemId"),
                Objects.requireNonNull(revisionNumber, "revisionNumber"));
    }

    public OrderItemId orderItemId() {
        return orderItemId;
    }

    public RevisionNumber revisionNumber() {
        return revisionNumber;
    }
}

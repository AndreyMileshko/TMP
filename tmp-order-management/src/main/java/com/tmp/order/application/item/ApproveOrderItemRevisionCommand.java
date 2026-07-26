package com.tmp.order.application.item;

import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.RevisionNumber;
import java.util.Objects;

/**
 * Internal command to approve the current Draft Revision (Specification §6.4).
 */
public final class ApproveOrderItemRevisionCommand {

    private final OrderItemId orderItemId;
    private final RevisionNumber revisionNumber;

    public ApproveOrderItemRevisionCommand(OrderItemId orderItemId, RevisionNumber revisionNumber) {
        this.orderItemId = Objects.requireNonNull(orderItemId, "orderItemId");
        this.revisionNumber = Objects.requireNonNull(revisionNumber, "revisionNumber");
    }

    public OrderItemId orderItemId() {
        return orderItemId;
    }

    public RevisionNumber revisionNumber() {
        return revisionNumber;
    }
}

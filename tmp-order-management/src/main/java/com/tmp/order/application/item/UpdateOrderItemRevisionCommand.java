package com.tmp.order.application.item;

import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.application.payload.OrderItemRevisionPayloadLine;
import com.tmp.order.domain.OrderedQuantity;
import java.util.List;
import java.util.Objects;

/**
 * Internal command to update the current Draft Revision (Specification §6.3).
 */
public final class UpdateOrderItemRevisionCommand {

    private final OrderItemId orderItemId;
    private final RevisionNumber revisionNumber;
    private final OrderedQuantity orderedQuantity;
    private final List<OrderItemRevisionPayloadLine> lines;

    public UpdateOrderItemRevisionCommand(
            OrderItemId orderItemId,
            RevisionNumber revisionNumber,
            OrderedQuantity orderedQuantity,
            List<OrderItemRevisionPayloadLine> lines) {
        this.orderItemId = Objects.requireNonNull(orderItemId, "orderItemId");
        this.revisionNumber = Objects.requireNonNull(revisionNumber, "revisionNumber");
        this.orderedQuantity = Objects.requireNonNull(orderedQuantity, "orderedQuantity");
        this.lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
    }

    public OrderItemId orderItemId() {
        return orderItemId;
    }

    public RevisionNumber revisionNumber() {
        return revisionNumber;
    }

    public OrderedQuantity orderedQuantity() {
        return orderedQuantity;
    }

    public List<OrderItemRevisionPayloadLine> lines() {
        return lines;
    }
}

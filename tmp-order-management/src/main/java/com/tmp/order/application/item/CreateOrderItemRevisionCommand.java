package com.tmp.order.application.item;

import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.RevisionNumber;
import java.util.Objects;
import java.util.Optional;

/**
 * Internal command to create Draft Revision N+1 for an ACTIVE order item (Specification §6.2).
 */
public final class CreateOrderItemRevisionCommand {

    private final OrderItemId orderItemId;
    private final RevisionNumber revisionNumber;
    private final RevisionNumber copyFromRevisionNumber;

    public CreateOrderItemRevisionCommand(
            OrderItemId orderItemId,
            RevisionNumber revisionNumber,
            RevisionNumber copyFromRevisionNumber) {
        this.orderItemId = Objects.requireNonNull(orderItemId, "orderItemId");
        this.revisionNumber = Objects.requireNonNull(revisionNumber, "revisionNumber");
        this.copyFromRevisionNumber = copyFromRevisionNumber;
    }

    public OrderItemId orderItemId() {
        return orderItemId;
    }

    public RevisionNumber revisionNumber() {
        return revisionNumber;
    }

    public Optional<RevisionNumber> copyFromRevisionNumber() {
        return Optional.ofNullable(copyFromRevisionNumber);
    }
}

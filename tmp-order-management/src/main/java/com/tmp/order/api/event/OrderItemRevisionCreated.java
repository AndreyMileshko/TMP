package com.tmp.order.api.event;

import com.tmp.core.api.event.AbstractDomainEvent;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.RevisionNumber;
import java.util.Objects;
import java.util.Optional;

/**
 * Published when a Draft Order Item Revision is created (Specification §17).
 */
public final class OrderItemRevisionCreated extends AbstractDomainEvent {

    public static final String SOURCE_CAPABILITY_ID = OrderCreated.SOURCE_CAPABILITY_ID;

    private final OrderItemId orderItemId;
    private final RevisionNumber revisionNumber;
    private final String actor;

    public OrderItemRevisionCreated(OrderItemId orderItemId, RevisionNumber revisionNumber) {
        this(orderItemId, revisionNumber, null);
    }

    public OrderItemRevisionCreated(
            OrderItemId orderItemId, RevisionNumber revisionNumber, String actor) {
        super(SOURCE_CAPABILITY_ID);
        this.orderItemId = Objects.requireNonNull(orderItemId, "orderItemId");
        this.revisionNumber = Objects.requireNonNull(revisionNumber, "revisionNumber");
        this.actor = normalizeOptional(actor);
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public OrderItemId orderItemId() {
        return orderItemId;
    }

    public RevisionNumber revisionNumber() {
        return revisionNumber;
    }

    public Optional<String> actor() {
        return Optional.ofNullable(actor);
    }
}

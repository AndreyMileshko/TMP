package com.tmp.order.api.event;

import com.tmp.core.api.event.AbstractDomainEvent;
import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.RevisionNumber;
import java.util.Objects;
import java.util.Optional;

/**
 * Published after a successful {@code ORDER_ITEM_REVISION_APPROVE} post (Specification §17).
 *
 * <p>Consumed by Production in a later Stage; Stage 5 only publishes the event.
 */
public final class OrderItemRevisionApproved extends AbstractDomainEvent {

    public static final String SOURCE_CAPABILITY_ID = OrderCreated.SOURCE_CAPABILITY_ID;

    private final OrderId orderId;
    private final OrderItemId orderItemId;
    private final RevisionNumber revisionNumber;
    private final String correlationId;
    private final String actor;

    public OrderItemRevisionApproved(
            OrderId orderId,
            OrderItemId orderItemId,
            RevisionNumber revisionNumber,
            String correlationId) {
        this(orderId, orderItemId, revisionNumber, correlationId, null);
    }

    public OrderItemRevisionApproved(
            OrderId orderId,
            OrderItemId orderItemId,
            RevisionNumber revisionNumber,
            String correlationId,
            String actor) {
        super(SOURCE_CAPABILITY_ID);
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.orderItemId = Objects.requireNonNull(orderItemId, "orderItemId");
        this.revisionNumber = Objects.requireNonNull(revisionNumber, "revisionNumber");
        this.correlationId = requireNonBlank(correlationId, "correlationId");
        this.actor = normalizeOptional(actor);
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return trimmed;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public OrderId orderId() {
        return orderId;
    }

    public OrderItemId orderItemId() {
        return orderItemId;
    }

    public RevisionNumber revisionNumber() {
        return revisionNumber;
    }

    public String correlationId() {
        return correlationId;
    }

    public Optional<String> actor() {
        return Optional.ofNullable(actor);
    }
}

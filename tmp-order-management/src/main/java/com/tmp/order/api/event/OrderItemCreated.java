package com.tmp.order.api.event;

import com.tmp.core.api.event.AbstractDomainEvent;
import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemId;
import java.util.Objects;
import java.util.Optional;

/**
 * Published after a successful {@code ORDER_ITEM_CREATE} post (Specification §17).
 */
public final class OrderItemCreated extends AbstractDomainEvent {

    public static final String SOURCE_CAPABILITY_ID = OrderCreated.SOURCE_CAPABILITY_ID;

    private final OrderId orderId;
    private final OrderItemId orderItemId;
    private final String actor;

    public OrderItemCreated(OrderId orderId, OrderItemId orderItemId) {
        this(orderId, orderItemId, null);
    }

    public OrderItemCreated(OrderId orderId, OrderItemId orderItemId, String actor) {
        super(SOURCE_CAPABILITY_ID);
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.orderItemId = Objects.requireNonNull(orderItemId, "orderItemId");
        this.actor = normalizeOptional(actor);
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

    public Optional<String> actor() {
        return Optional.ofNullable(actor);
    }
}

package com.tmp.order.api.event;

import com.tmp.core.api.event.AbstractDomainEvent;
import com.tmp.order.api.OrderId;
import java.util.Objects;
import java.util.Optional;

/**
 * Published after a successful {@code ORDER_ACTIVATE} post (Specification §8.2 / ADR-031).
 */
public final class OrderActivated extends AbstractDomainEvent {

    public static final String SOURCE_CAPABILITY_ID = OrderCreated.SOURCE_CAPABILITY_ID;

    private final OrderId orderId;
    private final String actor;

    public OrderActivated(OrderId orderId) {
        this(orderId, null);
    }

    public OrderActivated(OrderId orderId, String actor) {
        super(SOURCE_CAPABILITY_ID);
        this.orderId = Objects.requireNonNull(orderId, "orderId");
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

    public Optional<String> actor() {
        return Optional.ofNullable(actor);
    }
}

package com.tmp.order.api.event;

import com.tmp.core.api.event.AbstractDomainEvent;
import com.tmp.order.api.OrderId;
import java.util.Objects;
import java.util.Optional;

/**
 * Published after a successful {@code ORDER_CREATE} post (Specification §17).
 *
 * <p>{@code actor} is optional: {@link com.tmp.document.api.DocumentOperationContext} currently
 * exposes document metadata only, so actor may be absent when not available at publish time.
 */
public final class OrderCreated extends AbstractDomainEvent {

    public static final String SOURCE_CAPABILITY_ID = "order-management";

    private final OrderId orderId;
    private final String correlationId;
    private final String actor;

    public OrderCreated(OrderId orderId, String correlationId) {
        this(orderId, correlationId, null);
    }

    public OrderCreated(OrderId orderId, String correlationId, String actor) {
        super(SOURCE_CAPABILITY_ID);
        this.orderId = Objects.requireNonNull(orderId, "orderId");
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

    public String correlationId() {
        return correlationId;
    }

    public Optional<String> actor() {
        return Optional.ofNullable(actor);
    }
}

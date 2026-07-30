package com.tmp.order.application.order;

import com.tmp.order.api.OrderId;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Thrown when {@code approveOrder} preconditions fail (Specification §8.2 / ADR-030): at least one
 * {@code ACTIVE} order item and complete commercial data are required. Does not involve Production
 * Status.
 */
public final class OrderApprovalRejectedException extends RuntimeException {

    private final OrderId orderId;
    private final List<String> missingFields;

    public OrderApprovalRejectedException(OrderId orderId, String reason) {
        this(orderId, reason, List.of());
    }

    public OrderApprovalRejectedException(
            OrderId orderId, String reason, List<String> missingFields) {
        super(Objects.requireNonNull(reason, "reason")
                + ": "
                + Objects.requireNonNull(orderId, "orderId"));
        this.orderId = orderId;
        this.missingFields =
                Collections.unmodifiableList(
                        Objects.requireNonNull(missingFields, "missingFields"));
    }

    public OrderId orderId() {
        return orderId;
    }

    public List<String> missingFields() {
        return missingFields;
    }
}

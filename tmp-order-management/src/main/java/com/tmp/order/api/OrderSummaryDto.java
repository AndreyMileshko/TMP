package com.tmp.order.api;

import java.time.Instant;
import java.util.Objects;

/**
 * Brief read-only view of a customer order for list / search results (Specification §15.1.3).
 *
 * <p>Contains only Order Management commercial data. Does not expose domain aggregates, persistence
 * entities, Production Status, stock or cutting data.
 */
public final class OrderSummaryDto {

    private final OrderId orderId;
    private final String orderNumber;
    private final OrderStatus status;
    private final String customerRef;
    private final String customerName;
    private final Instant createdAt;

    private OrderSummaryDto(
            OrderId orderId,
            String orderNumber,
            OrderStatus status,
            String customerRef,
            String customerName,
            Instant createdAt) {
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.status = status;
        this.customerRef = customerRef;
        this.customerName = customerName;
        this.createdAt = createdAt;
    }

    public static OrderSummaryDto of(
            OrderId orderId,
            String orderNumber,
            OrderStatus status,
            String customerRef,
            String customerName,
            Instant createdAt) {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(orderNumber, "orderNumber");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(customerName, "customerName");
        Objects.requireNonNull(createdAt, "createdAt");
        return new OrderSummaryDto(
                orderId, orderNumber, status, customerRef, customerName, createdAt);
    }

    public OrderId orderId() {
        return orderId;
    }

    public String orderNumber() {
        return orderNumber;
    }

    public OrderStatus status() {
        return status;
    }

    public String customerRef() {
        return customerRef;
    }

    public String customerName() {
        return customerName;
    }

    public Instant createdAt() {
        return createdAt;
    }
}

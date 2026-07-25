package com.tmp.order.api;

import java.time.Instant;
import java.util.Objects;

/**
 * Full read-only view of a customer order (Specification §15.1.3).
 *
 * <p>Contains only Order Management commercial header fields. Direction and currency are plain
 * strings so that domain types are not leaked into the public API.
 */
public final class OrderDto {

    private final OrderId orderId;
    private final String orderNumber;
    private final OrderStatus status;
    private final String customerRef;
    private final String customerName;
    private final String contractRef;
    private final String siteRef;
    private final String responsibleManager;
    private final String direction;
    private final String currency;
    private final Instant createdAt;
    private final Instant updatedAt;

    private OrderDto(
            OrderId orderId,
            String orderNumber,
            OrderStatus status,
            String customerRef,
            String customerName,
            String contractRef,
            String siteRef,
            String responsibleManager,
            String direction,
            String currency,
            Instant createdAt,
            Instant updatedAt) {
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.status = status;
        this.customerRef = customerRef;
        this.customerName = customerName;
        this.contractRef = contractRef;
        this.siteRef = siteRef;
        this.responsibleManager = responsibleManager;
        this.direction = direction;
        this.currency = currency;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static OrderDto of(
            OrderId orderId,
            String orderNumber,
            OrderStatus status,
            String customerRef,
            String customerName,
            String contractRef,
            String siteRef,
            String responsibleManager,
            String direction,
            String currency,
            Instant createdAt,
            Instant updatedAt) {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(orderNumber, "orderNumber");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(customerName, "customerName");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        return new OrderDto(
                orderId,
                orderNumber,
                status,
                customerRef,
                customerName,
                contractRef,
                siteRef,
                responsibleManager,
                direction,
                currency,
                createdAt,
                updatedAt);
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

    public String contractRef() {
        return contractRef;
    }

    public String siteRef() {
        return siteRef;
    }

    public String responsibleManager() {
        return responsibleManager;
    }

    public String direction() {
        return direction;
    }

    public String currency() {
        return currency;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}

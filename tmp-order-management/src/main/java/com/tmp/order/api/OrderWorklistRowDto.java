package com.tmp.order.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Commercial read row for the operational Orders worklist. Contains only Order Management data.
 *
 * <p>{@code itemQuantity} is the historical ordered quantity of all items (not only ACTIVE).
 * {@code customerName} / {@code customerRef} may be {@code null} for incomplete DRAFT orders.
 * {@code readyDate} is the optional planned manufacturing date (UI: «Дата изготовления»).
 */
public final class OrderWorklistRowDto {

    private final OrderId orderId;
    private final String orderNumber;
    private final OrderStatus status;
    private final String customerRef;
    private final String customerName;
    private final Instant createdAt;
    private final LocalDate readyDate;
    private final long itemQuantity;

    private OrderWorklistRowDto(
            OrderId orderId,
            String orderNumber,
            OrderStatus status,
            String customerRef,
            String customerName,
            Instant createdAt,
            LocalDate readyDate,
            long itemQuantity) {
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.status = status;
        this.customerRef = customerRef;
        this.customerName = customerName;
        this.createdAt = createdAt;
        this.readyDate = readyDate;
        this.itemQuantity = itemQuantity;
    }

    public static OrderWorklistRowDto of(
            OrderId orderId,
            String orderNumber,
            OrderStatus status,
            String customerRef,
            String customerName,
            Instant createdAt,
            long itemQuantity) {
        return of(orderId, orderNumber, status, customerRef, customerName, createdAt, null, itemQuantity);
    }

    public static OrderWorklistRowDto of(
            OrderId orderId,
            String orderNumber,
            OrderStatus status,
            String customerRef,
            String customerName,
            Instant createdAt,
            LocalDate readyDate,
            long itemQuantity) {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(orderNumber, "orderNumber");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        if (itemQuantity < 0L) {
            throw new IllegalArgumentException("itemQuantity must be >= 0: " + itemQuantity);
        }
        return new OrderWorklistRowDto(
                orderId,
                orderNumber,
                status,
                customerRef,
                customerName,
                createdAt,
                readyDate,
                itemQuantity);
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

    /** Optional planned ready date; {@code null} when absent. */
    public LocalDate readyDate() {
        return readyDate;
    }

    public long itemQuantity() {
        return itemQuantity;
    }
}

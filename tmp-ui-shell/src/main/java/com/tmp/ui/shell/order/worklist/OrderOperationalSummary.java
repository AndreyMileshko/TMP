package com.tmp.ui.shell.order.worklist;

import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Operational Orders list row. Commercial identity plus derived user-facing status.
 */
public final class OrderOperationalSummary {

    private final OrderId orderId;
    private final String orderNumber;
    private final String customerRef;
    private final String customerName;
    private final Instant createdAt;
    private final LocalDate readyDate;
    private final long itemQuantity;
    private final OrderStatus commercialStatus;
    private final OrderOperationalStatus operationalStatus;

    public OrderOperationalSummary(
            OrderId orderId,
            String orderNumber,
            String customerRef,
            String customerName,
            Instant createdAt,
            long itemQuantity,
            OrderStatus commercialStatus,
            OrderOperationalStatus operationalStatus) {
        this(
                orderId,
                orderNumber,
                customerRef,
                customerName,
                createdAt,
                null,
                itemQuantity,
                commercialStatus,
                operationalStatus);
    }

    public OrderOperationalSummary(
            OrderId orderId,
            String orderNumber,
            String customerRef,
            String customerName,
            Instant createdAt,
            LocalDate readyDate,
            long itemQuantity,
            OrderStatus commercialStatus,
            OrderOperationalStatus operationalStatus) {
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.orderNumber = Objects.requireNonNull(orderNumber, "orderNumber");
        this.customerRef = customerRef;
        this.customerName = customerName;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.readyDate = readyDate;
        if (itemQuantity < 0L) {
            throw new IllegalArgumentException("itemQuantity must be >= 0: " + itemQuantity);
        }
        this.itemQuantity = itemQuantity;
        this.commercialStatus = Objects.requireNonNull(commercialStatus, "commercialStatus");
        this.operationalStatus = Objects.requireNonNull(operationalStatus, "operationalStatus");
    }

    public OrderId orderId() {
        return orderId;
    }

    public String orderNumber() {
        return orderNumber;
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

    /** Optional planned manufacturing date; {@code null} when absent. */
    public LocalDate readyDate() {
        return readyDate;
    }

    public long itemQuantity() {
        return itemQuantity;
    }

    public OrderStatus commercialStatus() {
        return commercialStatus;
    }

    public OrderOperationalStatus operationalStatus() {
        return operationalStatus;
    }
}

package com.tmp.order.api;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Production-facing read-only view of a customer order for whole-order Launch.
 *
 * <p>Includes order status, the count of {@link OrderItemStatus#ACTIVE} items, and only ACTIVE
 * items that currently have an approved Production-facing specification.
 */
public final class OrderForProductionDto {

    private final OrderId orderId;
    private final OrderStatus status;
    private final int activeItemCount;
    private final List<OrderItemForProductionDto> items;

    private OrderForProductionDto(
            OrderId orderId,
            OrderStatus status,
            int activeItemCount,
            List<OrderItemForProductionDto> items) {
        this.orderId = orderId;
        this.status = status;
        this.activeItemCount = activeItemCount;
        this.items = List.copyOf(items);
    }

    public static OrderForProductionDto of(
            OrderId orderId,
            OrderStatus status,
            int activeItemCount,
            List<OrderItemForProductionDto> items) {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(items, "items");
        if (activeItemCount < 0) {
            throw new IllegalArgumentException("activeItemCount must be >= 0: " + activeItemCount);
        }
        return new OrderForProductionDto(orderId, status, activeItemCount, items);
    }

    public OrderId orderId() {
        return orderId;
    }

    public OrderStatus status() {
        return status;
    }

    public int activeItemCount() {
        return activeItemCount;
    }

    public List<OrderItemForProductionDto> items() {
        return Collections.unmodifiableList(items);
    }
}

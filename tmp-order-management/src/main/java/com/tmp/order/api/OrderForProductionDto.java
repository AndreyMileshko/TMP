package com.tmp.order.api;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Production-facing read-only view of a customer order for whole-order Launch.
 *
 * <p>Includes order status, the count of {@link OrderItemStatus#ACTIVE} items, ACTIVE items that
 * currently have an approved Production-facing specification, and explicit IDs of ACTIVE items
 * that lack such a specification.
 */
public final class OrderForProductionDto {

    private final OrderId orderId;
    private final OrderStatus status;
    private final int activeItemCount;
    private final List<OrderItemForProductionDto> items;
    private final List<OrderItemId> missingSpecificationItemIds;

    private OrderForProductionDto(
            OrderId orderId,
            OrderStatus status,
            int activeItemCount,
            List<OrderItemForProductionDto> items,
            List<OrderItemId> missingSpecificationItemIds) {
        this.orderId = orderId;
        this.status = status;
        this.activeItemCount = activeItemCount;
        this.items = List.copyOf(items);
        this.missingSpecificationItemIds = List.copyOf(missingSpecificationItemIds);
    }

    public static OrderForProductionDto of(
            OrderId orderId,
            OrderStatus status,
            int activeItemCount,
            List<OrderItemForProductionDto> items,
            List<OrderItemId> missingSpecificationItemIds) {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(missingSpecificationItemIds, "missingSpecificationItemIds");
        if (activeItemCount < 0) {
            throw new IllegalArgumentException("activeItemCount must be >= 0: " + activeItemCount);
        }
        if (activeItemCount != items.size() + missingSpecificationItemIds.size()) {
            throw new IllegalArgumentException(
                    "activeItemCount must equal items + missingSpecificationItemIds: "
                            + activeItemCount
                            + " != "
                            + items.size()
                            + " + "
                            + missingSpecificationItemIds.size());
        }
        return new OrderForProductionDto(
                orderId, status, activeItemCount, items, missingSpecificationItemIds);
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

    /**
     * ACTIVE order items that have no current approved Production-facing specification.
     */
    public List<OrderItemId> missingSpecificationItemIds() {
        return Collections.unmodifiableList(missingSpecificationItemIds);
    }
}

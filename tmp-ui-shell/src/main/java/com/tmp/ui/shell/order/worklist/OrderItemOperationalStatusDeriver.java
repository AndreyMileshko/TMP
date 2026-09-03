package com.tmp.ui.shell.order.worklist;

import com.tmp.order.api.OrderItemStatus;
import com.tmp.order.api.OrderStatus;
import com.tmp.production.api.ProductionQueryApi.ItemProductionStateStatus;
import com.tmp.production.api.ProductionQueryApi.ItemProductionStateView;
import java.util.Objects;

/**
 * Pure derivation of user-facing item operational status from parent Order commercial state,
 * commercial item status, and a discriminated Production read result. Does not query repositories.
 *
 * <p>{@link OrderItemOperationalStatus#EDITING} is used only when the item is actually mutable
 * (parent {@code DRAFT} and item {@code DRAFT}). A non-editable item on an order not yet transferred
 * to work is {@link OrderItemOperationalStatus#READY_FOR_TRANSFER}.
 *
 * <p>Successful empty Production state (not yet accepted) is {@link
 * OrderItemOperationalStatus#AWAITING_PRODUCTION}. Access denied / technical failure is {@link
 * OrderItemOperationalStatus#STATUS_UNAVAILABLE} — never invent zeros as awaiting.
 */
public final class OrderItemOperationalStatusDeriver {

    private OrderItemOperationalStatusDeriver() {}

    public static OrderItemOperationalStatus derive(
            OrderStatus parentOrderStatus,
            OrderItemStatus itemStatus,
            ItemProductionReadResult productionRead) {
        Objects.requireNonNull(parentOrderStatus, "parentOrderStatus");
        Objects.requireNonNull(itemStatus, "itemStatus");
        Objects.requireNonNull(productionRead, "productionRead");

        if (itemStatus == OrderItemStatus.CANCELLED) {
            return OrderItemOperationalStatus.CANCELLED;
        }
        if (parentOrderStatus == OrderStatus.CANCELLED) {
            return OrderItemOperationalStatus.CANCELLED;
        }
        if (parentOrderStatus == OrderStatus.DRAFT || parentOrderStatus == OrderStatus.APPROVED) {
            return isItemDataEditable(parentOrderStatus, itemStatus)
                    ? OrderItemOperationalStatus.EDITING
                    : OrderItemOperationalStatus.READY_FOR_TRANSFER;
        }
        // Parent ACTIVE (transferred to work)
        return switch (productionRead) {
            case ItemProductionReadResult.SuccessWithState(var state) -> deriveFromProduction(state);
            case ItemProductionReadResult.SuccessNotAccepted() ->
                    OrderItemOperationalStatus.AWAITING_PRODUCTION;
            case ItemProductionReadResult.Unavailable() ->
                    OrderItemOperationalStatus.STATUS_UNAVAILABLE;
        };
    }

    private static OrderItemOperationalStatus deriveFromProduction(ItemProductionStateView state) {
        long ordered = state.orderedQuantity();
        long released = state.releasedQuantity();
        boolean cancelled = state.status() == ItemProductionStateStatus.CANCELLED;

        if (cancelled) {
            return released > 0L
                    ? OrderItemOperationalStatus.PARTIALLY_COMPLETED
                    : OrderItemOperationalStatus.CANCELLED;
        }
        if (ordered > 0L && released >= ordered) {
            return OrderItemOperationalStatus.COMPLETED;
        }
        if (state.status() == ItemProductionStateStatus.RELEASED) {
            return OrderItemOperationalStatus.COMPLETED;
        }
        if (released > 0L) {
            return OrderItemOperationalStatus.IN_PRODUCTION;
        }
        return OrderItemOperationalStatus.AWAITING_PRODUCTION;
    }

    /**
     * Actual item-card mutability: parent Order still {@code DRAFT} and the item itself is still
     * {@code DRAFT}. Leftover {@code APPROVED} parent and {@code ACTIVE} items are not editable.
     */
    public static boolean isItemDataEditable(OrderStatus parentOrderStatus, OrderItemStatus itemStatus) {
        return parentOrderStatus == OrderStatus.DRAFT && itemStatus == OrderItemStatus.DRAFT;
    }

    public static boolean isItemCancellable(OrderStatus parentOrderStatus, OrderItemStatus itemStatus) {
        return isItemDataEditable(parentOrderStatus, itemStatus);
    }
}

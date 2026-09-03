package com.tmp.ui.shell.order.worklist;

import com.tmp.order.api.OrderItemStatus;
import com.tmp.order.api.OrderStatus;
import com.tmp.production.api.ProductionQueryApi.ItemProductionStateStatus;
import com.tmp.production.api.ProductionQueryApi.ItemProductionStateView;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure derivation of user-facing item operational status from parent Order commercial state,
 * commercial item status, and optional Production item facts. Does not query repositories.
 *
 * <p>Missing Production facts ({@link Optional#empty()}) after transfer mean the production-derived
 * status is unavailable — never treated as zero manufactured.
 */
public final class OrderItemOperationalStatusDeriver {

    private OrderItemOperationalStatusDeriver() {}

    public static OrderItemOperationalStatus derive(
            OrderStatus parentOrderStatus,
            OrderItemStatus itemStatus,
            Optional<ItemProductionStateView> productionFacts) {
        Objects.requireNonNull(parentOrderStatus, "parentOrderStatus");
        Objects.requireNonNull(itemStatus, "itemStatus");
        Objects.requireNonNull(productionFacts, "productionFacts");

        if (itemStatus == OrderItemStatus.CANCELLED) {
            return OrderItemOperationalStatus.CANCELLED;
        }
        if (parentOrderStatus == OrderStatus.DRAFT || parentOrderStatus == OrderStatus.APPROVED) {
            return OrderItemOperationalStatus.EDITING;
        }
        if (parentOrderStatus == OrderStatus.CANCELLED) {
            return OrderItemOperationalStatus.CANCELLED;
        }
        // Parent ACTIVE (transferred to work)
        return productionFacts
                .map(OrderItemOperationalStatusDeriver::deriveFromProduction)
                .orElse(OrderItemOperationalStatus.STATUS_UNAVAILABLE);
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
}

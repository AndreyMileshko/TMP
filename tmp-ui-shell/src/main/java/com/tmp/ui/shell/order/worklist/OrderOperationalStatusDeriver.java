package com.tmp.ui.shell.order.worklist;

import com.tmp.order.api.OrderStatus;
import com.tmp.production.api.ProductionQueryApi.OrderProductionListFacts;
import com.tmp.production.api.ProductionQueryApi.OrderProductionViewStatus;
import java.util.Objects;

/**
 * Pure derivation of user-facing operational status from commercial Order Management state and
 * Production facts. Does not query repositories.
 */
public final class OrderOperationalStatusDeriver {

    private OrderOperationalStatusDeriver() {}

    public static OrderOperationalStatus derive(
            OrderStatus commercialStatus, long orderedQuantity, OrderProductionListFacts facts) {
        Objects.requireNonNull(commercialStatus, "commercialStatus");
        if (orderedQuantity < 0L) {
            throw new IllegalArgumentException("orderedQuantity must be >= 0: " + orderedQuantity);
        }
        OrderProductionListFacts production = facts == null ? emptyFacts() : facts;
        return switch (commercialStatus) {
            case DRAFT, APPROVED -> OrderOperationalStatus.EDITING;
            case CANCELLED -> OrderOperationalStatus.CANCELLED;
            case ACTIVE -> deriveActive(orderedQuantity, production);
        };
    }

    private static OrderOperationalStatus deriveActive(
            long orderedQuantity, OrderProductionListFacts production) {
        long released = production.releasedQuantity();
        long active = production.activeProductionQuantity();
        boolean cancelledRemainder =
                production.cancellationPosted()
                        || production.status() == OrderProductionViewStatus.CANCELLED;
        if (cancelledRemainder) {
            return released > 0L
                    ? OrderOperationalStatus.PARTIALLY_COMPLETED
                    : OrderOperationalStatus.CANCELLED;
        }
        if (orderedQuantity > 0L && released >= orderedQuantity && active == 0L) {
            return OrderOperationalStatus.COMPLETED;
        }
        if (production.status() == OrderProductionViewStatus.MANUFACTURED && active == 0L) {
            return OrderOperationalStatus.COMPLETED;
        }
        if (released > 0L) {
            return OrderOperationalStatus.IN_PRODUCTION;
        }
        return OrderOperationalStatus.AWAITING_PRODUCTION;
    }

    private static OrderProductionListFacts emptyFacts() {
        return new OrderProductionListFacts(
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000000"),
                OrderProductionViewStatus.NOT_ACCEPTED,
                0L,
                0L,
                0L,
                false);
    }
}

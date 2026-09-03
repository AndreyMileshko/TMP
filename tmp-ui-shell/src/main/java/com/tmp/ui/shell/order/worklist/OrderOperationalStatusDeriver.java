package com.tmp.ui.shell.order.worklist;

import com.tmp.order.api.OrderStatus;
import com.tmp.production.api.ProductionQueryApi.OrderProductionListFacts;
import com.tmp.production.api.ProductionQueryApi.OrderProductionViewStatus;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure derivation of user-facing operational status from commercial Order Management state and
 * Production facts. Does not query repositories.
 *
 * <p>Missing Production facts ({@link Optional#empty()}) mean the production-derived status is
 * unavailable — never treated as zero manufactured.
 */
public final class OrderOperationalStatusDeriver {

    private OrderOperationalStatusDeriver() {}

    public static OrderOperationalStatus derive(
            OrderStatus commercialStatus, long orderedQuantity, OrderProductionListFacts facts) {
        return derive(commercialStatus, orderedQuantity, Optional.ofNullable(facts));
    }

    public static OrderOperationalStatus derive(
            OrderStatus commercialStatus,
            long orderedQuantity,
            Optional<OrderProductionListFacts> facts) {
        Objects.requireNonNull(commercialStatus, "commercialStatus");
        Objects.requireNonNull(facts, "facts");
        if (orderedQuantity < 0L) {
            throw new IllegalArgumentException("orderedQuantity must be >= 0: " + orderedQuantity);
        }
        return switch (commercialStatus) {
            case DRAFT, APPROVED -> OrderOperationalStatus.EDITING;
            case CANCELLED -> OrderOperationalStatus.CANCELLED;
            case ACTIVE -> facts
                    .map(production -> deriveActive(orderedQuantity, production))
                    .orElse(OrderOperationalStatus.STATUS_UNAVAILABLE);
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
}

package com.tmp.ui.shell.screen.production;

import com.tmp.production.api.ProductionQueryApi.OrderProductionViewStatus;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Production state filter for the LEVEL 1 tree. Presentation-only; no persisted states. */
public enum ProductionTreeStatusFilter {
    IN_PROGRESS("В работе"),
    MANUFACTURED("Изготовлены"),
    CANCELLED("Отменены"),
    ALL("Все");

    private final String caption;

    ProductionTreeStatusFilter(String caption) {
        this.caption = caption;
    }

    public String caption() {
        return caption;
    }

    public boolean matches(OrderProductionViewStatus status) {
        Objects.requireNonNull(status, "status");
        return switch (this) {
            case IN_PROGRESS -> status == OrderProductionViewStatus.NOT_ACCEPTED
                    || status == OrderProductionViewStatus.IN_PRODUCTION;
            case MANUFACTURED -> status == OrderProductionViewStatus.MANUFACTURED;
            case CANCELLED -> status == OrderProductionViewStatus.CANCELLED;
            case ALL -> true;
        };
    }

    public Set<OrderProductionViewStatus> includedStatuses() {
        return switch (this) {
            case IN_PROGRESS -> EnumSet.of(
                    OrderProductionViewStatus.NOT_ACCEPTED, OrderProductionViewStatus.IN_PRODUCTION);
            case MANUFACTURED -> EnumSet.of(OrderProductionViewStatus.MANUFACTURED);
            case CANCELLED -> EnumSet.of(OrderProductionViewStatus.CANCELLED);
            case ALL -> EnumSet.allOf(OrderProductionViewStatus.class);
        };
    }
}

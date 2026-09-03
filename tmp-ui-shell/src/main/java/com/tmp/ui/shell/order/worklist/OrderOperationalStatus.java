package com.tmp.ui.shell.order.worklist;

/**
 * User-facing operational status of an order on the Orders worklist. Separate from commercial
 * {@code OrderStatus}.
 */
public enum OrderOperationalStatus {
    EDITING("Редактируется", "tmp-status-dot-neutral"),
    AWAITING_PRODUCTION("Ожидает производства", "tmp-status-dot-warning"),
    IN_PRODUCTION("В производстве", "tmp-status-dot-info"),
    COMPLETED("Выполнен", "tmp-status-dot-success"),
    PARTIALLY_COMPLETED("Частично выполнен", "tmp-status-dot-warning-strong"),
    CANCELLED("Отменён", "tmp-status-dot-danger");

    private final String caption;
    private final String indicatorStyleClass;

    OrderOperationalStatus(String caption, String indicatorStyleClass) {
        this.caption = caption;
        this.indicatorStyleClass = indicatorStyleClass;
    }

    public String caption() {
        return caption;
    }

    public String indicatorStyleClass() {
        return indicatorStyleClass;
    }
}

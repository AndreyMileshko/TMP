package com.tmp.ui.shell.order.worklist;

/**
 * User-facing operational status of an order item. Separate from commercial {@code OrderItemStatus}
 * and from Production {@code ItemProductionStateStatus}.
 */
public enum OrderItemOperationalStatus {
    EDITING("Редактируется", "tmp-status-dot-neutral"),
    READY_FOR_TRANSFER("Готова к передаче", "tmp-status-dot-ready"),
    AWAITING_PRODUCTION("Ожидает производства", "tmp-status-dot-warning"),
    IN_PRODUCTION("В производстве", "tmp-status-dot-info"),
    COMPLETED("Выполнена", "tmp-status-dot-success"),
    PARTIALLY_COMPLETED("Частично выполнена", "tmp-status-dot-warning-strong"),
    CANCELLED("Отменена", "tmp-status-dot-danger"),
    /** Presentation-only: Production facts could not be read. Never treated as zero produced. */
    STATUS_UNAVAILABLE("Статус недоступен", "tmp-status-dot-unavailable");

    private final String caption;
    private final String indicatorStyleClass;

    OrderItemOperationalStatus(String caption, String indicatorStyleClass) {
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

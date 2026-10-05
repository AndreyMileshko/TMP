package com.tmp.ui.shell.screen.production;

import com.tmp.ui.shell.order.worklist.OrderListPeriod;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Persistent Production workbench period filter. Quick search and status filter are not stored
 * here.
 */
public final class ProductionWorkbenchFilterPreference {

    public static final String NAMESPACE = "ui.production.workbench.v1";
    public static final String KEY = "period";
    public static final int VERSION = 1;

    private final OrderListPeriod.Preset periodPreset;
    private final LocalDate customFrom;
    private final LocalDate customTo;

    public ProductionWorkbenchFilterPreference(
            OrderListPeriod.Preset periodPreset, LocalDate customFrom, LocalDate customTo) {
        this.periodPreset = Objects.requireNonNull(periodPreset, "periodPreset");
        this.customFrom = customFrom;
        this.customTo = customTo;
    }

    public static ProductionWorkbenchFilterPreference defaults() {
        return new ProductionWorkbenchFilterPreference(
                OrderListPeriod.Preset.LAST_30_DAYS, null, null);
    }

    public OrderListPeriod.Preset periodPreset() {
        return periodPreset;
    }

    public LocalDate customFrom() {
        return customFrom;
    }

    public LocalDate customTo() {
        return customTo;
    }
}

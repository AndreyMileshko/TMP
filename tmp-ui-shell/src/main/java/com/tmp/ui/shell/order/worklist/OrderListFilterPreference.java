package com.tmp.ui.shell.order.worklist;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Persistent Orders list filters. Quick search, page index and selected row are not stored here.
 */
public final class OrderListFilterPreference {

    public static final String NAMESPACE = "ui.orders.list.v1";
    public static final String KEY = "filters";
    public static final int VERSION = 2;
    public static final int LEGACY_VERSION = 1;

    private final Set<OrderOperationalStatus> statuses;
    private final boolean selectAllCustomers;
    private final Set<CustomerFilterKey> customerKeys;
    private final OrderListPeriod.Preset periodPreset;
    private final LocalDate customFrom;
    private final LocalDate customTo;
    private final int pageSize;

    public OrderListFilterPreference(
            Set<OrderOperationalStatus> statuses,
            boolean selectAllCustomers,
            Set<CustomerFilterKey> customerKeys,
            OrderListPeriod.Preset periodPreset,
            LocalDate customFrom,
            LocalDate customTo,
            int pageSize) {
        this.statuses = Set.copyOf(Objects.requireNonNull(statuses, "statuses"));
        this.selectAllCustomers = selectAllCustomers;
        this.customerKeys = Set.copyOf(Objects.requireNonNull(customerKeys, "customerKeys"));
        this.periodPreset = Objects.requireNonNull(periodPreset, "periodPreset");
        this.customFrom = customFrom;
        this.customTo = customTo;
        this.pageSize = pageSize;
    }

    public static OrderListFilterPreference of(
            Set<OrderOperationalStatus> statuses,
            boolean selectAllCustomers,
            Set<String> customerRefs,
            boolean includeUnassignedCustomer,
            OrderListPeriod.Preset periodPreset,
            LocalDate customFrom,
            LocalDate customTo,
            int pageSize) {
        return new OrderListFilterPreference(
                statuses,
                selectAllCustomers,
                CustomerFilterKey.fromLegacyRefs(customerRefs, includeUnassignedCustomer),
                periodPreset,
                customFrom,
                customTo,
                pageSize);
    }

    public static OrderListFilterPreference defaults() {
        Set<OrderOperationalStatus> statuses = new LinkedHashSet<>();
        for (OrderOperationalStatus status : OrderOperationalStatus.values()) {
            if (status != OrderOperationalStatus.CANCELLED
                    && status != OrderOperationalStatus.STATUS_UNAVAILABLE) {
                statuses.add(status);
            }
        }
        return new OrderListFilterPreference(
                statuses,
                true,
                Set.of(),
                OrderListPeriod.Preset.LAST_30_DAYS,
                null,
                null,
                50);
    }

    public Set<OrderOperationalStatus> statuses() {
        return statuses;
    }

    public boolean selectAllCustomers() {
        return selectAllCustomers;
    }

    public Set<CustomerFilterKey> customerKeys() {
        return customerKeys;
    }

    public Set<String> customerRefs() {
        return CustomerFilterKey.parts(customerKeys).refs();
    }

    public Set<String> customerNames() {
        return CustomerFilterKey.parts(customerKeys).names();
    }

    public boolean includeUnassignedCustomer() {
        return CustomerFilterKey.parts(customerKeys).unassigned();
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

    public int pageSize() {
        return pageSize;
    }
}

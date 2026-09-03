package com.tmp.ui.shell.order.worklist;

import com.tmp.order.api.OrderId;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

/**
 * Session-only Orders list memento for Shell Back/Forward. Not persisted between logins.
 */
public final class OrderListMemento {

    private final String quickSearch;
    private final Set<OrderOperationalStatus> statuses;
    private final boolean selectAllCustomers;
    private final Set<String> customerRefs;
    private final boolean includeUnassignedCustomer;
    private final OrderListPeriod.Preset periodPreset;
    private final LocalDate customFrom;
    private final LocalDate customTo;
    private final int pageIndex;
    private final OrderId selectedOrderId;

    public OrderListMemento(
            String quickSearch,
            Set<OrderOperationalStatus> statuses,
            boolean selectAllCustomers,
            Set<String> customerRefs,
            boolean includeUnassignedCustomer,
            OrderListPeriod.Preset periodPreset,
            LocalDate customFrom,
            LocalDate customTo,
            int pageIndex,
            OrderId selectedOrderId) {
        this.quickSearch = quickSearch == null ? "" : quickSearch;
        this.statuses = Set.copyOf(Objects.requireNonNull(statuses, "statuses"));
        this.selectAllCustomers = selectAllCustomers;
        this.customerRefs = Set.copyOf(Objects.requireNonNull(customerRefs, "customerRefs"));
        this.includeUnassignedCustomer = includeUnassignedCustomer;
        this.periodPreset = Objects.requireNonNull(periodPreset, "periodPreset");
        this.customFrom = customFrom;
        this.customTo = customTo;
        this.pageIndex = pageIndex;
        this.selectedOrderId = selectedOrderId;
    }

    public String quickSearch() {
        return quickSearch;
    }

    public Set<OrderOperationalStatus> statuses() {
        return statuses;
    }

    public boolean selectAllCustomers() {
        return selectAllCustomers;
    }

    public Set<String> customerRefs() {
        return customerRefs;
    }

    public boolean includeUnassignedCustomer() {
        return includeUnassignedCustomer;
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

    public int pageIndex() {
        return pageIndex;
    }

    public OrderId selectedOrderId() {
        return selectedOrderId;
    }
}

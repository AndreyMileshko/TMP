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
    private final Set<CustomerFilterKey> customerKeys;
    private final OrderListPeriod.Preset periodPreset;
    private final LocalDate customFrom;
    private final LocalDate customTo;
    private final int pageIndex;
    private final OrderId selectedOrderId;

    public OrderListMemento(
            String quickSearch,
            Set<OrderOperationalStatus> statuses,
            boolean selectAllCustomers,
            Set<CustomerFilterKey> customerKeys,
            OrderListPeriod.Preset periodPreset,
            LocalDate customFrom,
            LocalDate customTo,
            int pageIndex,
            OrderId selectedOrderId) {
        this.quickSearch = quickSearch == null ? "" : quickSearch;
        this.statuses = Set.copyOf(Objects.requireNonNull(statuses, "statuses"));
        this.selectAllCustomers = selectAllCustomers;
        this.customerKeys = Set.copyOf(Objects.requireNonNull(customerKeys, "customerKeys"));
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

    public Set<CustomerFilterKey> customerKeys() {
        return customerKeys;
    }

    public Set<String> customerRefs() {
        return CustomerFilterKey.parts(customerKeys).refs();
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

    public int pageIndex() {
        return pageIndex;
    }

    public OrderId selectedOrderId() {
        return selectedOrderId;
    }
}

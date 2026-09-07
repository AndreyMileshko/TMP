package com.tmp.ui.shell.order.worklist;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Filter request for the operational Orders list. Period is mandatory. Sort is applied to the full
 * matched set, then pagination.
 */
public final class OrderOperationalListRequest {

    private final Instant createdFrom;
    private final Instant createdToExclusive;
    private final String quickSearch;
    private final Set<OrderOperationalStatus> statuses;
    private final Set<String> customerRefs;
    private final Set<String> customerNames;
    private final boolean includeUnassignedCustomer;
    private final boolean filterByCustomers;
    private final int pageIndex;
    private final int pageSize;
    private final OrderListSortField sortField;
    private final OrderListSortDirection sortDirection;

    public OrderOperationalListRequest(
            Instant createdFrom,
            Instant createdToExclusive,
            String quickSearch,
            Set<OrderOperationalStatus> statuses,
            Set<String> customerRefs,
            boolean includeUnassignedCustomer,
            boolean filterByCustomers,
            int pageIndex,
            int pageSize) {
        this(
                createdFrom,
                createdToExclusive,
                quickSearch,
                statuses,
                customerRefs,
                Set.of(),
                includeUnassignedCustomer,
                filterByCustomers,
                pageIndex,
                pageSize,
                OrderOperationalListSorter.defaultField(),
                OrderOperationalListSorter.defaultDirection());
    }

    public OrderOperationalListRequest(
            Instant createdFrom,
            Instant createdToExclusive,
            String quickSearch,
            Set<OrderOperationalStatus> statuses,
            Set<String> customerRefs,
            Set<String> customerNames,
            boolean includeUnassignedCustomer,
            boolean filterByCustomers,
            int pageIndex,
            int pageSize) {
        this(
                createdFrom,
                createdToExclusive,
                quickSearch,
                statuses,
                customerRefs,
                customerNames,
                includeUnassignedCustomer,
                filterByCustomers,
                pageIndex,
                pageSize,
                OrderOperationalListSorter.defaultField(),
                OrderOperationalListSorter.defaultDirection());
    }

    public OrderOperationalListRequest(
            Instant createdFrom,
            Instant createdToExclusive,
            String quickSearch,
            Set<OrderOperationalStatus> statuses,
            Set<String> customerRefs,
            Set<String> customerNames,
            boolean includeUnassignedCustomer,
            boolean filterByCustomers,
            int pageIndex,
            int pageSize,
            OrderListSortField sortField,
            OrderListSortDirection sortDirection) {
        this.createdFrom = Objects.requireNonNull(createdFrom, "createdFrom");
        this.createdToExclusive = Objects.requireNonNull(createdToExclusive, "createdToExclusive");
        if (!createdFrom.isBefore(createdToExclusive)) {
            throw new IllegalArgumentException("createdFrom must be before createdToExclusive");
        }
        this.quickSearch = quickSearch;
        this.statuses = Set.copyOf(Objects.requireNonNull(statuses, "statuses"));
        this.customerRefs = Set.copyOf(Objects.requireNonNull(customerRefs, "customerRefs"));
        this.customerNames = Set.copyOf(Objects.requireNonNull(customerNames, "customerNames"));
        this.includeUnassignedCustomer = includeUnassignedCustomer;
        this.filterByCustomers = filterByCustomers;
        if (pageIndex < 0) {
            throw new IllegalArgumentException("pageIndex must be >= 0: " + pageIndex);
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be >= 1: " + pageSize);
        }
        this.pageIndex = pageIndex;
        this.pageSize = pageSize;
        this.sortField = Objects.requireNonNull(sortField, "sortField");
        this.sortDirection = Objects.requireNonNull(sortDirection, "sortDirection");
    }

    public Instant createdFrom() {
        return createdFrom;
    }

    public Instant createdToExclusive() {
        return createdToExclusive;
    }

    public String quickSearch() {
        return quickSearch;
    }

    public Set<OrderOperationalStatus> statuses() {
        return statuses;
    }

    public Set<String> customerRefs() {
        return customerRefs;
    }

    public Set<String> customerNames() {
        return customerNames;
    }

    public boolean includeUnassignedCustomer() {
        return includeUnassignedCustomer;
    }

    public boolean filterByCustomers() {
        return filterByCustomers;
    }

    public int pageIndex() {
        return pageIndex;
    }

    public int pageSize() {
        return pageSize;
    }

    public OrderListSortField sortField() {
        return sortField;
    }

    public OrderListSortDirection sortDirection() {
        return sortDirection;
    }
}

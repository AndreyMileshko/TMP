package com.tmp.order.api;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Bounded commercial criteria for the Orders worklist read. Period is mandatory
 * ({@code createdAt >= createdFrom} and {@code createdAt < createdToExclusive}).
 *
 * <p>{@code quickSearch} matches order number <em>or</em> customer name (partial,
 * case-insensitive). Customer filter uses stable {@code customerRef} values, legacy
 * null-ref {@code customerName} values, and true unassigned (neither ref nor name).
 * When {@link #filterByCustomers()} is {@code false} the customer predicate is absent.
 */
public final class OrderWorklistCriteria {

    public static final int MAX_ROWS = 10_000;

    private final Instant createdFrom;
    private final Instant createdToExclusive;
    private final String quickSearch;
    private final Set<String> customerRefs;
    private final Set<String> customerNames;
    private final boolean includeUnassignedCustomer;
    private final boolean filterByCustomers;

    private OrderWorklistCriteria(
            Instant createdFrom,
            Instant createdToExclusive,
            String quickSearch,
            Set<String> customerRefs,
            Set<String> customerNames,
            boolean includeUnassignedCustomer,
            boolean filterByCustomers) {
        this.createdFrom = createdFrom;
        this.createdToExclusive = createdToExclusive;
        this.quickSearch = quickSearch;
        this.customerRefs = Set.copyOf(customerRefs);
        this.customerNames = Set.copyOf(customerNames);
        this.includeUnassignedCustomer = includeUnassignedCustomer;
        this.filterByCustomers = filterByCustomers;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Instant createdFrom() {
        return createdFrom;
    }

    public Instant createdToExclusive() {
        return createdToExclusive;
    }

    public Optional<String> quickSearch() {
        return Optional.ofNullable(quickSearch);
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

    public static final class Builder {

        private Instant createdFrom;
        private Instant createdToExclusive;
        private String quickSearch;
        private Set<String> customerRefs = Set.of();
        private Set<String> customerNames = Set.of();
        private boolean includeUnassignedCustomer;
        private boolean filterByCustomers;

        private Builder() {}

        public Builder createdFrom(Instant createdFrom) {
            this.createdFrom = createdFrom;
            return this;
        }

        public Builder createdToExclusive(Instant createdToExclusive) {
            this.createdToExclusive = createdToExclusive;
            return this;
        }

        public Builder quickSearch(String quickSearch) {
            this.quickSearch = quickSearch;
            return this;
        }

        public Builder customerRefs(Set<String> customerRefs) {
            this.customerRefs = customerRefs == null ? Set.of() : new LinkedHashSet<>(customerRefs);
            return this;
        }

        public Builder customerNames(Set<String> customerNames) {
            this.customerNames = customerNames == null ? Set.of() : new LinkedHashSet<>(customerNames);
            return this;
        }

        public Builder includeUnassignedCustomer(boolean includeUnassignedCustomer) {
            this.includeUnassignedCustomer = includeUnassignedCustomer;
            return this;
        }

        public Builder filterByCustomers(boolean filterByCustomers) {
            this.filterByCustomers = filterByCustomers;
            return this;
        }

        public OrderWorklistCriteria build() {
            Objects.requireNonNull(createdFrom, "createdFrom");
            Objects.requireNonNull(createdToExclusive, "createdToExclusive");
            if (!createdFrom.isBefore(createdToExclusive)) {
                throw new IllegalArgumentException(
                        "createdFrom must be before createdToExclusive: "
                                + createdFrom
                                + " / "
                                + createdToExclusive);
            }
            return new OrderWorklistCriteria(
                    createdFrom,
                    createdToExclusive,
                    normalize(quickSearch),
                    copyNonBlank(customerRefs),
                    copyNonBlank(customerNames),
                    includeUnassignedCustomer,
                    filterByCustomers);
        }

        private static Set<String> copyNonBlank(Set<String> values) {
            Set<String> copied = new LinkedHashSet<>();
            for (String value : values) {
                if (value == null) {
                    continue;
                }
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    copied.add(trimmed);
                }
            }
            return copied;
        }

        private static String normalize(String value) {
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
    }
}

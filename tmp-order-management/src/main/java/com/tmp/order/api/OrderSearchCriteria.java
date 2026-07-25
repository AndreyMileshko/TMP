package com.tmp.order.api;

import java.time.Instant;
import java.util.Optional;

/**
 * Search criteria for {@link OrderQueryService#searchOrders(OrderSearchCriteria, PageRequest)}
 * (Specification §15.1.1).
 *
 * <p>Only filters that exist on the Customer Order model are allowed. Blank optional strings are
 * normalized to absent ({@link Optional#empty()}).
 */
public final class OrderSearchCriteria {

    private final String orderNumber;
    private final OrderStatus orderStatus;
    private final String customerRef;
    private final String customerName;
    private final Instant createdFrom;
    private final Instant createdTo;

    private OrderSearchCriteria(
            String orderNumber,
            OrderStatus orderStatus,
            String customerRef,
            String customerName,
            Instant createdFrom,
            Instant createdTo) {
        this.orderNumber = orderNumber;
        this.orderStatus = orderStatus;
        this.customerRef = customerRef;
        this.customerName = customerName;
        this.createdFrom = createdFrom;
        this.createdTo = createdTo;
    }

    /** Empty criteria (no filters). */
    public static OrderSearchCriteria empty() {
        return new OrderSearchCriteria(null, null, null, null, null, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<String> orderNumber() {
        return Optional.ofNullable(orderNumber);
    }

    public Optional<OrderStatus> orderStatus() {
        return Optional.ofNullable(orderStatus);
    }

    public Optional<String> customerRef() {
        return Optional.ofNullable(customerRef);
    }

    public Optional<String> customerName() {
        return Optional.ofNullable(customerName);
    }

    public Optional<Instant> createdFrom() {
        return Optional.ofNullable(createdFrom);
    }

    public Optional<Instant> createdTo() {
        return Optional.ofNullable(createdTo);
    }

    public static final class Builder {

        private String orderNumber;
        private OrderStatus orderStatus;
        private String customerRef;
        private String customerName;
        private Instant createdFrom;
        private Instant createdTo;

        private Builder() {
        }

        public Builder orderNumber(String orderNumber) {
            this.orderNumber = orderNumber;
            return this;
        }

        public Builder orderStatus(OrderStatus orderStatus) {
            this.orderStatus = orderStatus;
            return this;
        }

        public Builder customerRef(String customerRef) {
            this.customerRef = customerRef;
            return this;
        }

        public Builder customerName(String customerName) {
            this.customerName = customerName;
            return this;
        }

        public Builder createdFrom(Instant createdFrom) {
            this.createdFrom = createdFrom;
            return this;
        }

        public Builder createdTo(Instant createdTo) {
            this.createdTo = createdTo;
            return this;
        }

        /**
         * Builds criteria with blank optional strings normalized to absent. If both
         * {@code createdFrom} and {@code createdTo} are set, {@code createdFrom} must not be after
         * {@code createdTo}.
         */
        public OrderSearchCriteria build() {
            if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
                throw new IllegalArgumentException(
                        "createdFrom must not be after createdTo: " + createdFrom + " > " + createdTo);
            }
            return new OrderSearchCriteria(
                    normalize(orderNumber),
                    orderStatus,
                    normalize(customerRef),
                    normalize(customerName),
                    createdFrom,
                    createdTo);
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

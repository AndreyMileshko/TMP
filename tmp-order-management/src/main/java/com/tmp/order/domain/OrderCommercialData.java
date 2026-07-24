package com.tmp.order.domain;

import java.util.Objects;

/**
 * Commercial header fields of a customer order editable only while the order is {@code DRAFT}
 * (Specification §5.1 / §8).
 */
public final class OrderCommercialData {

    private final String customerRef;
    private final String customerName;
    private final String contractRef;
    private final String siteRef;
    private final String responsibleManager;
    private final OrderDirection direction;
    private final CurrencyCode currency;

    private OrderCommercialData(
            String customerRef,
            String customerName,
            String contractRef,
            String siteRef,
            String responsibleManager,
            OrderDirection direction,
            CurrencyCode currency) {
        this.customerRef = customerRef;
        this.customerName = customerName;
        this.contractRef = contractRef;
        this.siteRef = siteRef;
        this.responsibleManager = responsibleManager;
        this.direction = direction;
        this.currency = currency;
    }

    public static OrderCommercialData of(
            String customerRef,
            String customerName,
            String contractRef,
            String siteRef,
            String responsibleManager,
            OrderDirection direction,
            CurrencyCode currency) {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(currency, "currency");
        String name = requireNonBlank(customerName, "customerName");
        return new OrderCommercialData(
                normalizeOptional(customerRef),
                name,
                normalizeOptional(contractRef),
                normalizeOptional(siteRef),
                normalizeOptional(responsibleManager),
                direction,
                currency);
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return trimmed;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public String customerRef() {
        return customerRef;
    }

    public String customerName() {
        return customerName;
    }

    public String contractRef() {
        return contractRef;
    }

    public String siteRef() {
        return siteRef;
    }

    public String responsibleManager() {
        return responsibleManager;
    }

    public OrderDirection direction() {
        return direction;
    }

    public CurrencyCode currency() {
        return currency;
    }
}

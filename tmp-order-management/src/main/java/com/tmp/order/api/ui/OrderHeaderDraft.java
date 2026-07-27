package com.tmp.order.api.ui;

import java.util.Objects;

/**
 * Immutable commercial header draft for order create / update document flows.
 *
 * <p>String fields mirror the commercial model of {@code OrderCreatePayload} /
 * {@code OrderUpdatePayload}. Domain conversion happens inside the UI application service.
 */
public final class OrderHeaderDraft {

    private final String orderNumber;
    private final String customerRef;
    private final String customerName;
    private final String contractRef;
    private final String siteRef;
    private final String responsibleManager;
    private final String direction;
    private final String currency;

    private OrderHeaderDraft(
            String orderNumber,
            String customerRef,
            String customerName,
            String contractRef,
            String siteRef,
            String responsibleManager,
            String direction,
            String currency) {
        this.orderNumber = orderNumber;
        this.customerRef = customerRef;
        this.customerName = customerName;
        this.contractRef = contractRef;
        this.siteRef = siteRef;
        this.responsibleManager = responsibleManager;
        this.direction = direction;
        this.currency = currency;
    }

    /**
     * Creates a validated header draft.
     *
     * @throws IllegalArgumentException if {@code customerName}, {@code orderNumber}, {@code
     *     direction}, or {@code currency} is null/blank
     */
    public static OrderHeaderDraft of(
            String orderNumber,
            String customerRef,
            String customerName,
            String contractRef,
            String siteRef,
            String responsibleManager,
            String direction,
            String currency) {
        return new OrderHeaderDraft(
                requireNonBlank(orderNumber, "orderNumber"),
                customerRef,
                requireNonBlank(customerName, "customerName"),
                contractRef,
                siteRef,
                responsibleManager,
                requireNonBlank(direction, "direction"),
                requireNonBlank(currency, "currency"));
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return trimmed;
    }

    public String orderNumber() {
        return orderNumber;
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

    public String direction() {
        return direction;
    }

    public String currency() {
        return currency;
    }
}

package com.tmp.order.api.ui;

import java.util.Objects;

/**
 * Immutable commercial fields draft for order item create / update document flows.
 */
public final class OrderItemCommercialDraft {

    private final String productCode;
    private final String name;
    private final String comments;
    private final String externalPositionNumber;

    private OrderItemCommercialDraft(
            String productCode, String name, String comments, String externalPositionNumber) {
        this.productCode = productCode;
        this.name = name;
        this.comments = comments;
        this.externalPositionNumber = externalPositionNumber;
    }

    /**
     * Creates a validated commercial draft.
     *
     * @throws IllegalArgumentException if {@code productCode} or {@code name} is null/blank
     */
    public static OrderItemCommercialDraft of(
            String productCode, String name, String comments, String externalPositionNumber) {
        return new OrderItemCommercialDraft(
                requireNonBlank(productCode, "productCode"),
                requireNonBlank(name, "name"),
                comments,
                normalizeOptional(externalPositionNumber));
    }

    public static OrderItemCommercialDraft of(String productCode, String name, String comments) {
        return of(productCode, name, comments, null);
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

    public String productCode() {
        return productCode;
    }

    public String name() {
        return name;
    }

    public String comments() {
        return comments;
    }

    public String externalPositionNumber() {
        return externalPositionNumber;
    }
}

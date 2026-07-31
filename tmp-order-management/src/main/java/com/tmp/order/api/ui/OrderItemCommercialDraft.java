package com.tmp.order.api.ui;

import java.util.Locale;
import java.util.Set;

/**
 * Immutable commercial fields draft for order item create / update document flows.
 *
 * <p>Per ADR-030 / STAGE5-052A, {@code productCode} and {@code name} may be absent ({@code null})
 * for incomplete DRAFT items. Blank input normalizes to {@code null}. Placeholders are rejected.
 */
public final class OrderItemCommercialDraft {

    private static final Set<String> FORBIDDEN_PLACEHOLDERS =
            Set.of("UNKNOWN", "N/A", "NA", "IMPORT", "—", "-", "NONE", "TBD");

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
     * Creates a commercial draft. {@code productCode} and {@code name} may be null/blank
     * (normalized to null).
     *
     * @throws IllegalArgumentException if a present value is a prohibited placeholder
     */
    public static OrderItemCommercialDraft of(
            String productCode, String name, String comments, String externalPositionNumber) {
        return new OrderItemCommercialDraft(
                normalizeOptionalRejectingPlaceholder(productCode, "productCode"),
                normalizeOptionalRejectingPlaceholder(name, "name"),
                normalizeOptional(comments),
                normalizeOptionalRejectingPlaceholder(
                        externalPositionNumber, "externalPositionNumber"));
    }

    public static OrderItemCommercialDraft of(String productCode, String name, String comments) {
        return of(productCode, name, comments, null);
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeOptionalRejectingPlaceholder(String value, String field) {
        String normalized = normalizeOptional(value);
        if (normalized != null) {
            String upper = normalized.toUpperCase(Locale.ROOT);
            if (FORBIDDEN_PLACEHOLDERS.contains(upper)) {
                throw new IllegalArgumentException(
                        field + " must not use placeholder value: " + normalized);
            }
        }
        return normalized;
    }

    /** Nullable for incomplete DRAFT. */
    public String productCode() {
        return productCode;
    }

    /** Nullable for incomplete DRAFT. */
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

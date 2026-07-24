package com.tmp.order.domain;

import java.util.Objects;

/**
 * Commercial fields of an order item that are not part of Revision / Specification
 * (Specification §5.2): product code, name, comments. Changed by {@code ORDER_ITEM_UPDATE}.
 */
public final class ItemCommercialData {

    private final ProductCode productCode;
    private final String name;
    private final String comments;

    private ItemCommercialData(ProductCode productCode, String name, String comments) {
        this.productCode = productCode;
        this.name = name;
        this.comments = comments;
    }

    public static ItemCommercialData of(ProductCode productCode, String name, String comments) {
        Objects.requireNonNull(productCode, "productCode");
        Objects.requireNonNull(name, "name");
        String trimmedName = name.trim();
        if (trimmedName.isEmpty()) {
            throw new IllegalArgumentException("Item name must not be blank");
        }
        String normalizedComments = comments == null ? null : comments.trim();
        if (normalizedComments != null && normalizedComments.isEmpty()) {
            normalizedComments = null;
        }
        return new ItemCommercialData(productCode, trimmedName, normalizedComments);
    }

    public ProductCode productCode() {
        return productCode;
    }

    public String name() {
        return name;
    }

    public String comments() {
        return comments;
    }
}

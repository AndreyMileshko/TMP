package com.tmp.order.domain;

import java.util.Objects;

/**
 * Commercial fields of an order item that are not part of Revision / Specification
 * (Specification §5.2): product code, name, comments, external position number. Changed by
 * {@code ORDER_ITEM_UPDATE}.
 */
public final class ItemCommercialData {

    private final ProductCode productCode;
    private final String name;
    private final String comments;
    private final String externalPositionNumber;

    private ItemCommercialData(
            ProductCode productCode,
            String name,
            String comments,
            String externalPositionNumber) {
        this.productCode = productCode;
        this.name = name;
        this.comments = comments;
        this.externalPositionNumber = externalPositionNumber;
    }

    public static ItemCommercialData of(
            ProductCode productCode, String name, String comments, String externalPositionNumber) {
        Objects.requireNonNull(productCode, "productCode");
        Objects.requireNonNull(name, "name");
        String trimmedName = name.trim();
        if (trimmedName.isEmpty()) {
            throw new IllegalArgumentException("Item name must not be blank");
        }
        String normalizedComments = CommercialPlaceholderValidator.normalizeOptional(comments);
        String normalizedExternal =
                CommercialPlaceholderValidator.normalizeOptional(externalPositionNumber);
        CommercialPlaceholderValidator.rejectPlaceholderIfPresent(
                normalizedExternal, "externalPositionNumber");
        return new ItemCommercialData(
                productCode, trimmedName, normalizedComments, normalizedExternal);
    }

    public static ItemCommercialData of(ProductCode productCode, String name, String comments) {
        return of(productCode, name, comments, null);
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

    public String externalPositionNumber() {
        return externalPositionNumber;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ItemCommercialData that)) {
            return false;
        }
        return productCode.equals(that.productCode)
                && name.equals(that.name)
                && Objects.equals(comments, that.comments)
                && Objects.equals(externalPositionNumber, that.externalPositionNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productCode, name, comments, externalPositionNumber);
    }
}

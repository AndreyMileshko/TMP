package com.tmp.order.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Commercial fields of an order item that are not part of Revision / Specification
 * (Specification §5.2): product code, name, comments, external position number. Changed by
 * {@code ORDER_ITEM_UPDATE}.
 *
 * <p>Per ADR-030 / STAGE5-052A, {@code productCode} and {@code name} may be {@code null} only while
 * the owning item is in {@code DRAFT}. Placeholders are rejected. Completeness is enforced before
 * {@code ORDER_ITEM_REVISION_APPROVE}.
 */
public final class ItemCommercialData {

    public static final String MISSING_PRODUCT_CODE_FIELD = "Код изделия";
    public static final String MISSING_NAME_FIELD = "Наименование изделия";

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

    /**
     * Creates commercial data. {@code productCode} and {@code name} may be null (incomplete DRAFT).
     * Blank name is normalized to null. Placeholders are rejected.
     */
    public static ItemCommercialData of(
            ProductCode productCode, String name, String comments, String externalPositionNumber) {
        if (productCode != null) {
            CommercialPlaceholderValidator.rejectPlaceholderIfPresent(
                    productCode.value(), "productCode");
        }
        String normalizedName = CommercialPlaceholderValidator.normalizeOptional(name);
        CommercialPlaceholderValidator.rejectPlaceholderIfPresent(normalizedName, "name");
        String normalizedComments = CommercialPlaceholderValidator.normalizeOptional(comments);
        String normalizedExternal =
                CommercialPlaceholderValidator.normalizeOptional(externalPositionNumber);
        CommercialPlaceholderValidator.rejectPlaceholderIfPresent(
                normalizedExternal, "externalPositionNumber");
        return new ItemCommercialData(
                productCode, normalizedName, normalizedComments, normalizedExternal);
    }

    public static ItemCommercialData of(ProductCode productCode, String name, String comments) {
        return of(productCode, name, comments, null);
    }

    /**
     * Builds commercial data from raw strings (blank → null). Does not invent placeholders.
     */
    public static ItemCommercialData ofRaw(
            String productCode, String name, String comments, String externalPositionNumber) {
        String normalizedCode = CommercialPlaceholderValidator.normalizeOptional(productCode);
        CommercialPlaceholderValidator.rejectPlaceholderIfPresent(normalizedCode, "productCode");
        ProductCode code = normalizedCode == null ? null : ProductCode.of(normalizedCode);
        return of(code, name, comments, externalPositionNumber);
    }

    public boolean hasProductCode() {
        return productCode != null;
    }

    public boolean hasName() {
        return name != null;
    }

    public boolean isProductCodeAbsent() {
        return productCode == null;
    }

    public boolean isNameAbsent() {
        return name == null;
    }

    /**
     * Stable display names of mandatory item commercial attributes missing for revision approval
     * (ADR-030).
     */
    public List<String> missingMandatoryFieldsForApproval() {
        List<String> missing = new ArrayList<>(2);
        if (productCode == null) {
            missing.add(MISSING_PRODUCT_CODE_FIELD);
        }
        if (name == null) {
            missing.add(MISSING_NAME_FIELD);
        }
        return Collections.unmodifiableList(missing);
    }

    public boolean isCompleteForApproval() {
        return missingMandatoryFieldsForApproval().isEmpty();
    }

    /** Nullable only for incomplete DRAFT items. */
    public ProductCode productCode() {
        return productCode;
    }

    /** Nullable only for incomplete DRAFT items. */
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
        return Objects.equals(productCode, that.productCode)
                && Objects.equals(name, that.name)
                && Objects.equals(comments, that.comments)
                && Objects.equals(externalPositionNumber, that.externalPositionNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productCode, name, comments, externalPositionNumber);
    }
}

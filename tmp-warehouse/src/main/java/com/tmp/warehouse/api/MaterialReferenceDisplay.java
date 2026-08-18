package com.tmp.warehouse.api;

import java.util.Objects;

/**
 * Read-only material reference display snapshot for Warehouse UI and Public API.
 *
 * <p>Warehouse does not own material master data. Display fields are resolved from the existing
 * MaterialReference context (Specification lines when available).
 */
public final class MaterialReferenceDisplay {

    private final String article;
    private final String materialName;
    private final String color;
    private final String size;
    private final String unitOfMeasure;

    private MaterialReferenceDisplay(
            String article,
            String materialName,
            String color,
            String size,
            String unitOfMeasure) {
        this.article = article;
        this.materialName = materialName;
        this.color = color;
        this.size = size;
        this.unitOfMeasure = unitOfMeasure;
    }

    public static MaterialReferenceDisplay ofArticleOnly(String article) {
        return new MaterialReferenceDisplay(
                requireNonBlank(article, "article"), "", "", "", "");
    }

    public static MaterialReferenceDisplay of(
            String article,
            String materialName,
            String color,
            String size,
            String unitOfMeasure) {
        return new MaterialReferenceDisplay(
                requireNonBlank(article, "article"),
                normalize(materialName),
                normalize(color),
                normalize(size),
                normalize(unitOfMeasure));
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return trimmed;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    public String article() {
        return article;
    }

    public String materialName() {
        return materialName;
    }

    public String color() {
        return color;
    }

    public String size() {
        return size;
    }

    public String unitOfMeasure() {
        return unitOfMeasure;
    }

    /** True when at least one descriptive field beyond article is present. */
    public boolean hasExtendedFields() {
        return !materialName.isEmpty()
                || !color.isEmpty()
                || !size.isEmpty()
                || !unitOfMeasure.isEmpty();
    }
}

package com.tmp.order.api;

import java.util.Objects;

/**
 * Read-only material reference display from ACTIVE Specification context (ADR-032).
 *
 * <p>Not a Material Master. Warehouse resolves display through this projection when available.
 */
public final class MaterialReferenceDisplayDto {

    private final String article;
    private final String materialName;
    private final String color;
    private final String size;
    private final String unitOfMeasure;

    private MaterialReferenceDisplayDto(
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

    public static MaterialReferenceDisplayDto of(
            String article,
            String materialName,
            String color,
            String size,
            String unitOfMeasure) {
        Objects.requireNonNull(article, "article");
        Objects.requireNonNull(materialName, "materialName");
        Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
        return new MaterialReferenceDisplayDto(
                article.trim(),
                materialName.trim(),
                color == null ? "" : color.trim(),
                size == null ? "" : size.trim(),
                unitOfMeasure.trim());
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
}

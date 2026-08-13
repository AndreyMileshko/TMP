package com.tmp.warehouse.domain;

import java.util.Objects;

/**
 * Warehouse-owned material reference used by stock state.
 *
 * <p>Identity is {@link MaterialReferenceId}. Business uniqueness is {@code article + color + size
 * + unitOfMeasure}; {@code name} is descriptive only. {@code unitOfMeasure} is restricted to {@link
 * UnitOfMeasure} canonical codes (empty only for legacy migrated rows).
 */
public final class MaterialReference {

    private final MaterialReferenceId id;
    private final String article;
    private final String name;
    private final String color;
    private final String size;
    private final String unitOfMeasure;

    private MaterialReference(
            MaterialReferenceId id,
            String article,
            String name,
            String color,
            String size,
            String unitOfMeasure) {
        this.id = Objects.requireNonNull(id, "id");
        this.article = article;
        this.name = name;
        this.color = color;
        this.size = size;
        this.unitOfMeasure = unitOfMeasure;
    }

    public static MaterialReference create(
            String article,
            String name,
            String color,
            String size,
            String unitOfMeasure) {
        return new MaterialReference(
                MaterialReferenceId.generate(),
                requireNonBlank(article, "article"),
                requireNonBlank(name, "name"),
                normalize(color),
                normalize(size),
                UnitOfMeasure.requireCode(unitOfMeasure));
    }

    public static MaterialReference rehydrate(
            MaterialReferenceId id,
            String article,
            String name,
            String color,
            String size,
            String unitOfMeasure) {
        return new MaterialReference(
                id,
                requireNonBlank(article, "article"),
                requireNonBlank(name, "name"),
                normalize(color),
                normalize(size),
                UnitOfMeasure.requirePersistedOrLegacyEmpty(unitOfMeasure));
    }

    /** Legacy migrated material identified only by article with empty variant fields. */
    public static MaterialReference legacyArticle(String article) {
        String code = requireNonBlank(article, "article");
        return new MaterialReference(
                MaterialReferenceId.generate(), code, code, "", "", "");
    }

    public MaterialReferenceId id() {
        return id;
    }

    public String article() {
        return article;
    }

    public String name() {
        return name;
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

    /** Backward-compatible alias for {@link #article()}. */
    public String materialCode() {
        return article;
    }

    public boolean matchesNaturalKey(
            String articleValue, String colorValue, String sizeValue, String unitValue) {
        return this.article.equals(requireNonBlank(articleValue, "article"))
                && this.color.equals(normalize(colorValue))
                && this.size.equals(normalize(sizeValue))
                && this.unitOfMeasure.equals(UnitOfMeasure.normalizeForKey(unitValue));
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

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MaterialReference that)) {
            return false;
        }
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return article;
    }
}

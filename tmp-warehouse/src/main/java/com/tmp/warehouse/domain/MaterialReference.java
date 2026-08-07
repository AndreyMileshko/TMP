package com.tmp.warehouse.domain;

import java.util.Objects;

/**
 * Material reference used by Warehouse stock state.
 *
 * <p>Warehouse does not own a Material Master. Material identity is taken from Order Management
 * Specification context (material code as the stable reference key).
 */
public final class MaterialReference {

    private final String materialCode;

    private MaterialReference(String materialCode) {
        this.materialCode = materialCode;
    }

    public static MaterialReference of(String materialCode) {
        return new MaterialReference(requireNonBlank(materialCode, "materialCode"));
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return trimmed;
    }

    public String materialCode() {
        return materialCode;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MaterialReference that)) {
            return false;
        }
        return materialCode.equals(that.materialCode);
    }

    @Override
    public int hashCode() {
        return materialCode.hashCode();
    }

    @Override
    public String toString() {
        return materialCode;
    }
}

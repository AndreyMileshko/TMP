package com.tmp.order.api.ui;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable UI DTO for one Draft Specification line submitted through {@code
 * ORDER_ITEM_REVISION_UPDATE}. Line numbers are assigned by the application service ({@code 1..N}).
 */
public final class OrderItemSpecificationLineDraft {

    private final String materialCode;
    private final String materialName;
    private final String color;
    private final BigDecimal lengthMm;
    private final BigDecimal lineQuantity;
    private final String unitOfMeasure;

    private OrderItemSpecificationLineDraft(
            String materialCode,
            String materialName,
            String color,
            BigDecimal lengthMm,
            BigDecimal lineQuantity,
            String unitOfMeasure) {
        this.materialCode = materialCode;
        this.materialName = materialName;
        this.color = color;
        this.lengthMm = lengthMm;
        this.lineQuantity = lineQuantity;
        this.unitOfMeasure = unitOfMeasure;
    }

    /**
     * Creates a validated line draft.
     *
     * @throws IllegalArgumentException if fields violate domain line rules
     */
    public static OrderItemSpecificationLineDraft of(
            String materialCode,
            String materialName,
            String color,
            BigDecimal lengthMm,
            BigDecimal lineQuantity,
            String unitOfMeasure) {
        String code = requireNonBlank(materialCode, "materialCode");
        String name = requireNonBlank(materialName, "materialName");
        String unit = requireNonBlank(unitOfMeasure, "unitOfMeasure");
        Objects.requireNonNull(lineQuantity, "lineQuantity");
        if (lineQuantity.signum() <= 0) {
            throw new IllegalArgumentException("lineQuantity must be > 0: " + lineQuantity);
        }
        if (lengthMm != null && lengthMm.signum() <= 0) {
            throw new IllegalArgumentException("lengthMm must be > 0 when present: " + lengthMm);
        }
        String normalizedColor = normalizeOptional(color);
        return new OrderItemSpecificationLineDraft(
                code, name, normalizedColor, lengthMm, lineQuantity, unit);
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

    public String materialCode() {
        return materialCode;
    }

    public String materialName() {
        return materialName;
    }

    public String color() {
        return color;
    }

    public BigDecimal lengthMm() {
        return lengthMm;
    }

    public BigDecimal lineQuantity() {
        return lineQuantity;
    }

    public String unitOfMeasure() {
        return unitOfMeasure;
    }
}

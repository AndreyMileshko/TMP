package com.tmp.order.api.imports;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One specification line of an import position. Blank {@code color} is normalized to {@code null}.
 * {@code quantity} is consumption per one product copy (never multiplied by item quantity).
 * Domain maps {@code length} → {@code lengthMm} and {@code quantity} → {@code lineQuantity}.
 */
public final class OrderImportSpecificationLine {

    private final String materialCode;
    private final String materialName;
    private final String color;
    private final BigDecimal length;
    private final String unitOfMeasure;
    private final BigDecimal quantity;

    private OrderImportSpecificationLine(
            String materialCode,
            String materialName,
            String color,
            BigDecimal length,
            String unitOfMeasure,
            BigDecimal quantity) {
        this.materialCode = materialCode;
        this.materialName = materialName;
        this.color = color;
        this.length = length;
        this.unitOfMeasure = unitOfMeasure;
        this.quantity = quantity;
    }

    /**
     * Creates a line. Prefer the overload that includes {@code unitOfMeasure}.
     *
     * @deprecated use {@link #of(String, String, String, BigDecimal, String, BigDecimal)}
     */
    @Deprecated
    public static OrderImportSpecificationLine of(
            String materialCode,
            String materialName,
            String color,
            BigDecimal lengthMm,
            BigDecimal lineQuantity) {
        return of(materialCode, materialName, color, lengthMm, null, lineQuantity);
    }

    public static OrderImportSpecificationLine of(
            String materialCode,
            String materialName,
            String color,
            BigDecimal length,
            String unitOfMeasure,
            BigDecimal quantity) {
        return new OrderImportSpecificationLine(
                materialCode,
                materialName,
                normalizeBlankToNull(color),
                length,
                normalizeBlankToNull(unitOfMeasure),
                quantity);
    }

    private static String normalizeBlankToNull(String value) {
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

    /** Nullable when colour is absent or blank in the source. */
    public String color() {
        return color;
    }

    /**
     * Nullable size/length from source «Размер». Mapped to domain {@code lengthMm} when numeric
     * mm; null after {@code кв.м.} transform.
     */
    public BigDecimal length() {
        return length;
    }

    /** Alias for {@link #length()} (legacy import callers). */
    public BigDecimal lengthMm() {
        return length;
    }

    /** Unit of measure from source; required for ACTIVE import validation. */
    public String unitOfMeasure() {
        return unitOfMeasure;
    }

    /**
     * Consumption per one product copy (source «Кол-во позиции на 1 изделие»). Never multiplied by
     * Order Item quantity.
     */
    public BigDecimal quantity() {
        return quantity;
    }

    /** Alias for {@link #quantity()} (legacy import callers). */
    public BigDecimal lineQuantity() {
        return quantity;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OrderImportSpecificationLine that)) {
            return false;
        }
        return Objects.equals(materialCode, that.materialCode)
                && Objects.equals(materialName, that.materialName)
                && Objects.equals(color, that.color)
                && compareNullable(length, that.length)
                && Objects.equals(unitOfMeasure, that.unitOfMeasure)
                && compareNullable(quantity, that.quantity);
    }

    private static boolean compareNullable(BigDecimal left, BigDecimal right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.compareTo(right) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                materialCode,
                materialName,
                color,
                length == null ? null : length.stripTrailingZeros(),
                unitOfMeasure,
                quantity == null ? null : quantity.stripTrailingZeros());
    }
}

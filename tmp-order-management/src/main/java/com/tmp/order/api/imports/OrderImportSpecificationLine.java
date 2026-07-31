package com.tmp.order.api.imports;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One specification line of an import position. Blank {@code color} is normalized to {@code null}.
 * {@code unitOfMeasure} is not part of the source-neutral model; Import Core applies domain default
 * «шт» when mapping to business documents.
 */
public final class OrderImportSpecificationLine {

    private final String materialCode;
    private final String materialName;
    private final String color;
    private final BigDecimal lengthMm;
    private final BigDecimal lineQuantity;

    private OrderImportSpecificationLine(
            String materialCode,
            String materialName,
            String color,
            BigDecimal lengthMm,
            BigDecimal lineQuantity) {
        this.materialCode = materialCode;
        this.materialName = materialName;
        this.color = color;
        this.lengthMm = lengthMm;
        this.lineQuantity = lineQuantity;
    }

    public static OrderImportSpecificationLine of(
            String materialCode,
            String materialName,
            String color,
            BigDecimal lengthMm,
            BigDecimal lineQuantity) {
        return new OrderImportSpecificationLine(
                materialCode, materialName, normalizeBlankToNull(color), lengthMm, lineQuantity);
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

    /** Nullable when length is not applicable. */
    public BigDecimal lengthMm() {
        return lengthMm;
    }

    public BigDecimal lineQuantity() {
        return lineQuantity;
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
                && compareNullable(lengthMm, that.lengthMm)
                && compareNullable(lineQuantity, that.lineQuantity);
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
                lengthMm == null ? null : lengthMm.stripTrailingZeros(),
                lineQuantity == null ? null : lineQuantity.stripTrailingZeros());
    }
}

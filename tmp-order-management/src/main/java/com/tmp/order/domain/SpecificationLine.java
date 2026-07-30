package com.tmp.order.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One line of an Item Specification (Specification §5.4): material reference, optional color and
 * length, line quantity and unit of measure. Does not carry stock balances, lots, production
 * quantities or orientation/placement.
 */
public final class SpecificationLine {

    private final String materialCode;
    private final String materialName;
    private final String color;
    private final BigDecimal lengthMm;
    private final BigDecimal lineQuantity;
    private final String unitOfMeasure;

    private SpecificationLine(
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

    public static SpecificationLine of(
            String materialCode,
            String materialName,
            String color,
            BigDecimal lengthMm,
            BigDecimal lineQuantity,
            String unitOfMeasure) {
        String code = requireNonBlank(materialCode, "materialCode");
        String name = requireNonBlank(materialName, "materialName");
        String unit = requireNonBlank(unitOfMeasure, "unitOfMeasure");
        String normalizedColor = CommercialPlaceholderValidator.normalizeOptional(color);
        CommercialPlaceholderValidator.rejectPlaceholderIfPresent(normalizedColor, "color");
        Objects.requireNonNull(lineQuantity, "lineQuantity");
        if (lineQuantity.signum() <= 0) {
            throw new IllegalArgumentException("lineQuantity must be > 0: " + lineQuantity);
        }
        if (lengthMm != null && lengthMm.signum() <= 0) {
            throw new IllegalArgumentException("lengthMm must be > 0 when present: " + lengthMm);
        }
        return new SpecificationLine(code, name, normalizedColor, lengthMm, lineQuantity, unit);
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

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SpecificationLine that)) {
            return false;
        }
        return materialCode.equals(that.materialCode)
                && materialName.equals(that.materialName)
                && Objects.equals(color, that.color)
                && compareNullable(lengthMm, that.lengthMm)
                && lineQuantity.compareTo(that.lineQuantity) == 0
                && unitOfMeasure.equals(that.unitOfMeasure);
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
                lineQuantity.stripTrailingZeros(),
                unitOfMeasure);
    }
}

package com.tmp.order.application.payload;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One typed specification line carried by a Revision payload (Specification §5.4 / §11.5).
 *
 * <p>Stored as a separate row in {@code order_item_revision_payload_line}; never as JSON or a
 * serialized blob. {@code lineQuantity} must be {@code > 0}; {@code lengthMm} when present must be
 * {@code > 0}.
 */
public final class OrderItemRevisionPayloadLine {

    private final int lineNumber;
    private final String materialCode;
    private final String materialName;
    private final String color;
    private final BigDecimal lengthMm;
    private final BigDecimal lineQuantity;
    private final String unitOfMeasure;

    private OrderItemRevisionPayloadLine(
            int lineNumber,
            String materialCode,
            String materialName,
            String color,
            BigDecimal lengthMm,
            BigDecimal lineQuantity,
            String unitOfMeasure) {
        if (lineNumber < 1) {
            throw new IllegalArgumentException("lineNumber must be >= 1: " + lineNumber);
        }
        this.lineNumber = lineNumber;
        this.materialCode = materialCode;
        this.materialName = materialName;
        this.color = color;
        this.lengthMm = lengthMm;
        this.lineQuantity = lineQuantity;
        this.unitOfMeasure = unitOfMeasure;
    }

    public static OrderItemRevisionPayloadLine of(
            int lineNumber,
            String materialCode,
            String materialName,
            String color,
            BigDecimal lengthMm,
            BigDecimal lineQuantity,
            String unitOfMeasure) {
        String code = requireNonBlank(materialCode, "materialCode");
        String name = requireNonBlank(materialName, "materialName");
        String unit = requireNonBlank(unitOfMeasure, "unitOfMeasure");
        String normalizedColor = normalizeOptional(color);
        Objects.requireNonNull(lineQuantity, "lineQuantity");
        if (lineQuantity.signum() <= 0) {
            throw new IllegalArgumentException("lineQuantity must be > 0: " + lineQuantity);
        }
        if (lengthMm != null && lengthMm.signum() <= 0) {
            throw new IllegalArgumentException("lengthMm must be > 0 when present: " + lengthMm);
        }
        return new OrderItemRevisionPayloadLine(
                lineNumber, code, name, normalizedColor, lengthMm, lineQuantity, unit);
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

    public int lineNumber() {
        return lineNumber;
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
        if (!(other instanceof OrderItemRevisionPayloadLine that)) {
            return false;
        }
        return lineNumber == that.lineNumber
                && materialCode.equals(that.materialCode)
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
                lineNumber,
                materialCode,
                materialName,
                color,
                lengthMm == null ? null : lengthMm.stripTrailingZeros(),
                lineQuantity.stripTrailingZeros(),
                unitOfMeasure);
    }
}

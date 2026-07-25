package com.tmp.order.application.payload;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One typed specification line carried by a Revision payload (Specification §5.4 / §11.5).
 *
 * <p>Stored as a separate row in {@code order_item_revision_payload_line}; never as JSON or a
 * serialized blob. Quantity must be {@code > 0}; consumption norm must be {@code >= 0}.
 */
public final class OrderItemRevisionPayloadLine {

    private final int lineNumber;
    private final String materialCode;
    private final String materialName;
    private final BigDecimal quantity;
    private final String unitOfMeasure;
    private final BigDecimal consumptionNorm;

    private OrderItemRevisionPayloadLine(
            int lineNumber,
            String materialCode,
            String materialName,
            BigDecimal quantity,
            String unitOfMeasure,
            BigDecimal consumptionNorm) {
        if (lineNumber < 1) {
            throw new IllegalArgumentException("lineNumber must be >= 1: " + lineNumber);
        }
        this.lineNumber = lineNumber;
        this.materialCode = materialCode;
        this.materialName = materialName;
        this.quantity = quantity;
        this.unitOfMeasure = unitOfMeasure;
        this.consumptionNorm = consumptionNorm;
    }

    public static OrderItemRevisionPayloadLine of(
            int lineNumber,
            String materialCode,
            String materialName,
            BigDecimal quantity,
            String unitOfMeasure,
            BigDecimal consumptionNorm) {
        String code = requireNonBlank(materialCode, "materialCode");
        String name = requireNonBlank(materialName, "materialName");
        String unit = requireNonBlank(unitOfMeasure, "unitOfMeasure");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(consumptionNorm, "consumptionNorm");
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException("Specification line quantity must be > 0: " + quantity);
        }
        if (consumptionNorm.signum() < 0) {
            throw new IllegalArgumentException(
                    "Consumption norm must be >= 0: " + consumptionNorm);
        }
        return new OrderItemRevisionPayloadLine(
                lineNumber, code, name, quantity, unit, consumptionNorm);
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return trimmed;
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

    public BigDecimal quantity() {
        return quantity;
    }

    public String unitOfMeasure() {
        return unitOfMeasure;
    }

    public BigDecimal consumptionNorm() {
        return consumptionNorm;
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
                && quantity.compareTo(that.quantity) == 0
                && unitOfMeasure.equals(that.unitOfMeasure)
                && consumptionNorm.compareTo(that.consumptionNorm) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                lineNumber,
                materialCode,
                materialName,
                quantity.stripTrailingZeros(),
                unitOfMeasure,
                consumptionNorm.stripTrailingZeros());
    }
}

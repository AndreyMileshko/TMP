package com.tmp.order.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One line of an Item Specification (Specification §5.4): material reference, quantity, unit of
 * measure and consumption norm. Does not carry stock balances, lots or production quantities.
 */
public final class SpecificationLine {

    private final String materialCode;
    private final String materialName;
    private final BigDecimal quantity;
    private final String unitOfMeasure;
    private final BigDecimal consumptionNorm;

    private SpecificationLine(
            String materialCode,
            String materialName,
            BigDecimal quantity,
            String unitOfMeasure,
            BigDecimal consumptionNorm) {
        this.materialCode = materialCode;
        this.materialName = materialName;
        this.quantity = quantity;
        this.unitOfMeasure = unitOfMeasure;
        this.consumptionNorm = consumptionNorm;
    }

    public static SpecificationLine of(
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
        return new SpecificationLine(code, name, quantity, unit, consumptionNorm);
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
        if (!(other instanceof SpecificationLine that)) {
            return false;
        }
        return materialCode.equals(that.materialCode)
                && materialName.equals(that.materialName)
                && quantity.compareTo(that.quantity) == 0
                && unitOfMeasure.equals(that.unitOfMeasure)
                && consumptionNorm.compareTo(that.consumptionNorm) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                materialCode,
                materialName,
                quantity.stripTrailingZeros(),
                unitOfMeasure,
                consumptionNorm.stripTrailingZeros());
    }
}

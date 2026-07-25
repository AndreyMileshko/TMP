package com.tmp.order.api;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Read-only line of an item specification (Specification §15.1.3). No stock balances, lots,
 * reserves or production quantities.
 */
public final class SpecificationLineDto {

    private final String materialCode;
    private final String materialName;
    private final BigDecimal quantity;
    private final String unitOfMeasure;
    private final BigDecimal consumptionNorm;

    private SpecificationLineDto(
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

    public static SpecificationLineDto of(
            String materialCode,
            String materialName,
            BigDecimal quantity,
            String unitOfMeasure,
            BigDecimal consumptionNorm) {
        Objects.requireNonNull(materialCode, "materialCode");
        Objects.requireNonNull(materialName, "materialName");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
        Objects.requireNonNull(consumptionNorm, "consumptionNorm");
        return new SpecificationLineDto(
                materialCode, materialName, quantity, unitOfMeasure, consumptionNorm);
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
}

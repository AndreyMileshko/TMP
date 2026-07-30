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
    private final String color;
    private final BigDecimal lengthMm;
    private final BigDecimal lineQuantity;
    private final String unitOfMeasure;

    private SpecificationLineDto(
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

    public static SpecificationLineDto of(
            String materialCode,
            String materialName,
            String color,
            BigDecimal lengthMm,
            BigDecimal lineQuantity,
            String unitOfMeasure) {
        Objects.requireNonNull(materialCode, "materialCode");
        Objects.requireNonNull(materialName, "materialName");
        Objects.requireNonNull(lineQuantity, "lineQuantity");
        Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
        return new SpecificationLineDto(
                materialCode, materialName, color, lengthMm, lineQuantity, unitOfMeasure);
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

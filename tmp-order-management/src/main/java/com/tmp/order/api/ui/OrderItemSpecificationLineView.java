package com.tmp.order.api.ui;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable UI read model for one Item Specification line. Does not expose domain aggregates.
 */
public final class OrderItemSpecificationLineView {

    private final int lineNumber;
    private final String materialCode;
    private final String materialName;
    private final String color;
    private final BigDecimal lengthMm;
    private final BigDecimal lineQuantity;
    private final String unitOfMeasure;

    private OrderItemSpecificationLineView(
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

    public static OrderItemSpecificationLineView of(
            int lineNumber,
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
        return new OrderItemSpecificationLineView(
                lineNumber,
                materialCode,
                materialName,
                color,
                lengthMm,
                lineQuantity,
                unitOfMeasure);
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
}

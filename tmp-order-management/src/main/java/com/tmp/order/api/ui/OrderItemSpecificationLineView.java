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
    private final BigDecimal quantity;
    private final String unitOfMeasure;
    private final BigDecimal consumptionNorm;

    private OrderItemSpecificationLineView(
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

    public static OrderItemSpecificationLineView of(
            int lineNumber,
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
        return new OrderItemSpecificationLineView(
                lineNumber,
                materialCode,
                materialName,
                quantity,
                unitOfMeasure,
                consumptionNorm);
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
}

package com.tmp.order.api.ui;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable UI DTO for one Draft Specification line submitted through {@code
 * ORDER_ITEM_REVISION_UPDATE}. Line numbers are assigned by the application service ({@code 1..N}).
 */
public final class OrderItemSpecificationLineDraft {

    private final String materialCode;
    private final String materialName;
    private final BigDecimal quantity;
    private final String unitOfMeasure;
    private final BigDecimal consumptionNorm;

    private OrderItemSpecificationLineDraft(
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

    /**
     * Creates a validated line draft.
     *
     * @throws IllegalArgumentException if fields violate domain line rules
     */
    public static OrderItemSpecificationLineDraft of(
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
            throw new IllegalArgumentException("Consumption norm must be >= 0: " + consumptionNorm);
        }
        return new OrderItemSpecificationLineDraft(code, name, quantity, unit, consumptionNorm);
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
}

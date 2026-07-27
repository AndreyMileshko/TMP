package com.tmp.ui.shell.screen.orderspecificationeditor;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Mutable working-copy row for the Specification editor table. Not a domain aggregate.
 */
public final class SpecificationLineRow {

    private String materialCode;
    private String materialName;
    private String quantity;
    private String unitOfMeasure;
    private String consumptionNorm;

    public SpecificationLineRow(
            String materialCode,
            String materialName,
            String quantity,
            String unitOfMeasure,
            String consumptionNorm) {
        this.materialCode = nullToEmpty(materialCode);
        this.materialName = nullToEmpty(materialName);
        this.quantity = nullToEmpty(quantity);
        this.unitOfMeasure = nullToEmpty(unitOfMeasure);
        this.consumptionNorm = nullToEmpty(consumptionNorm);
    }

    public static SpecificationLineRow blank() {
        return new SpecificationLineRow("", "", "", "", "0");
    }

    public SpecificationLineRow copy() {
        return new SpecificationLineRow(
                materialCode, materialName, quantity, unitOfMeasure, consumptionNorm);
    }

    public String materialCode() {
        return materialCode;
    }

    public void setMaterialCode(String materialCode) {
        this.materialCode = nullToEmpty(materialCode);
    }

    public String materialName() {
        return materialName;
    }

    public void setMaterialName(String materialName) {
        this.materialName = nullToEmpty(materialName);
    }

    public String quantity() {
        return quantity;
    }

    public void setQuantity(String quantity) {
        this.quantity = nullToEmpty(quantity);
    }

    public String unitOfMeasure() {
        return unitOfMeasure;
    }

    public void setUnitOfMeasure(String unitOfMeasure) {
        this.unitOfMeasure = nullToEmpty(unitOfMeasure);
    }

    public String consumptionNorm() {
        return consumptionNorm;
    }

    public void setConsumptionNorm(String consumptionNorm) {
        this.consumptionNorm = nullToEmpty(consumptionNorm);
    }

    public BigDecimal parseQuantity() {
        return parsePositive(quantity, "quantity");
    }

    public BigDecimal parseConsumptionNorm() {
        return parseNonNegative(consumptionNorm, "consumptionNorm");
    }

    public void requireValid() {
        requireNonBlank(materialCode, "materialCode");
        requireNonBlank(materialName, "materialName");
        requireNonBlank(unitOfMeasure, "unitOfMeasure");
        parseQuantity();
        parseConsumptionNorm();
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static BigDecimal parsePositive(String raw, String field) {
        BigDecimal value = parseDecimal(raw, field);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be > 0: " + raw);
        }
        return value;
    }

    private static BigDecimal parseNonNegative(String raw, String field) {
        BigDecimal value = parseDecimal(raw, field);
        if (value.signum() < 0) {
            throw new IllegalArgumentException(field + " must be >= 0: " + raw);
        }
        return value;
    }

    private static BigDecimal parseDecimal(String raw, String field) {
        Objects.requireNonNull(raw, field);
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        try {
            return new BigDecimal(trimmed);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(field + " must be a number: " + trimmed, ex);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

package com.tmp.ui.shell.screen.orderspecificationeditor;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Mutable working-copy row for the Specification editor table. Not a domain aggregate.
 */
public final class SpecificationLineRow {

    /** Default unit of measure for a new manually entered specification line. */
    public static final String DEFAULT_UNIT_OF_MEASURE = "шт";

    private String materialCode;
    private String materialName;
    private String color;
    private String lengthMm;
    private String lineQuantity;
    private String unitOfMeasure;

    public SpecificationLineRow(
            String materialCode,
            String materialName,
            String color,
            String lengthMm,
            String lineQuantity,
            String unitOfMeasure) {
        this.materialCode = nullToEmpty(materialCode);
        this.materialName = nullToEmpty(materialName);
        this.color = nullToEmpty(color);
        this.lengthMm = nullToEmpty(lengthMm);
        this.lineQuantity = nullToEmpty(lineQuantity);
        this.unitOfMeasure = nullToEmpty(unitOfMeasure);
    }

    public static SpecificationLineRow blank() {
        return new SpecificationLineRow("", "", "", "", "", DEFAULT_UNIT_OF_MEASURE);
    }

    public SpecificationLineRow copy() {
        return new SpecificationLineRow(
                materialCode, materialName, color, lengthMm, lineQuantity, unitOfMeasure);
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

    public String color() {
        return color;
    }

    public void setColor(String color) {
        this.color = nullToEmpty(color);
    }

    public String lengthMm() {
        return lengthMm;
    }

    public void setLengthMm(String lengthMm) {
        this.lengthMm = nullToEmpty(lengthMm);
    }

    public String lineQuantity() {
        return lineQuantity;
    }

    public void setLineQuantity(String lineQuantity) {
        this.lineQuantity = nullToEmpty(lineQuantity);
    }

    public String unitOfMeasure() {
        return unitOfMeasure;
    }

    public void setUnitOfMeasure(String unitOfMeasure) {
        this.unitOfMeasure = nullToEmpty(unitOfMeasure);
    }

    public String normalizedColor() {
        String trimmed = color.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public BigDecimal parseLengthMm() {
        String trimmed = lengthMm.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        BigDecimal value = parseDecimal(trimmed, "Длина, мм");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("Длина должна быть больше нуля или оставлена пустой.");
        }
        return value;
    }

    public BigDecimal parseLineQuantity() {
        BigDecimal value = parseDecimalRequired(lineQuantity, "Количество строки");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("Количество строки должно быть больше нуля.");
        }
        return value;
    }

    public void requireValid() {
        requireNonBlank(materialCode, "Укажите артикул материала.");
        requireNonBlank(materialName, "Укажите наименование материала.");
        requireNonBlank(unitOfMeasure, "Укажите единицу измерения.");
        parseLengthMm();
        parseLineQuantity();
    }

    private static void requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static BigDecimal parseDecimalRequired(String raw, String fieldName) {
        Objects.requireNonNull(raw, fieldName);
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " обязательно для заполнения.");
        }
        return parseDecimal(trimmed, fieldName);
    }

    private static BigDecimal parseDecimal(String trimmed, String fieldName) {
        try {
            return new BigDecimal(trimmed);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + " должно быть числом.", ex);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

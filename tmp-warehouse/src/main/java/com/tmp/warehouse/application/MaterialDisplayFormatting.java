package com.tmp.warehouse.application;

import java.math.BigDecimal;
import java.util.Objects;

/** Formats material display fields for Warehouse Public API and UI. */
public final class MaterialDisplayFormatting {

    private MaterialDisplayFormatting() {}

    public static String formatSizeFromLengthMm(BigDecimal lengthMm) {
        if (lengthMm == null) {
            return "";
        }
        return lengthMm.stripTrailingZeros().toPlainString() + " мм";
    }

    public static String formatDescription(
            String materialName, String color, String size, String unitOfMeasure) {
        StringBuilder builder = new StringBuilder();
        appendPart(builder, materialName);
        appendPart(builder, color);
        appendPart(builder, size);
        appendPart(builder, unitOfMeasure);
        return builder.toString();
    }

    private static void appendPart(StringBuilder builder, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append(" | ");
        }
        builder.append(value.trim());
    }

    public static String nonBlankOrEmpty(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    public static String requireArticle(String materialCode) {
        Objects.requireNonNull(materialCode, "materialCode");
        return materialCode.trim();
    }
}

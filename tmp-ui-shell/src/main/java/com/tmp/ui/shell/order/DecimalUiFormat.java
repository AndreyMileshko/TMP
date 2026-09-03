package com.tmp.ui.shell.order;

import java.math.BigDecimal;

/**
 * Presentation formatter for {@link BigDecimal} values shown in Tables / labels. Does not change
 * stored domain values.
 */
public final class DecimalUiFormat {

    private DecimalUiFormat() {}

    /**
     * Formats without insignificant trailing zeros and without scientific notation.
     *
     * <p>Examples: {@code 55.0000000 → "55"}, {@code 2.500000 → "2.5"}, {@code 0.125000 → "0.125"}.
     */
    public static String format(BigDecimal value) {
        if (value == null) {
            return "";
        }
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() < 0) {
            normalized = normalized.setScale(0);
        }
        return normalized.toPlainString();
    }

    /** Formats nullable length: empty string when absent. */
    public static String formatOptional(BigDecimal value) {
        return value == null ? "" : format(value);
    }
}

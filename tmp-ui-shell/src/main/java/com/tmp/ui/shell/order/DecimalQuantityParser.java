package com.tmp.ui.shell.order;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Parses user-entered quantity text for Russian UI. Accepts both {@code 0,5} and {@code 0.5}.
 * Returns {@link BigDecimal} — never {@code double}.
 */
public final class DecimalQuantityParser {

    private DecimalQuantityParser() {}

    /**
     * Parses a required decimal. Blank input and non-numeric text are rejected with Russian
     * messages.
     */
    public static BigDecimal parseRequired(String raw, String fieldLabel) {
        Objects.requireNonNull(fieldLabel, "fieldLabel");
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Укажите " + fieldLabel);
        }
        try {
            return new BigDecimal(normalizeDecimalText(value));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "Некорректное число для поля «" + fieldLabel + "»");
        }
    }

    /** Parses a required decimal that must be {@code > 0}. */
    public static BigDecimal parsePositive(String raw, String fieldLabel) {
        BigDecimal value = parseRequired(raw, fieldLabel);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(fieldLabel + " должно быть больше 0");
        }
        return value;
    }

    /** Parses a required decimal that must be {@code >= 0}. */
    public static BigDecimal parseNonNegative(String raw, String fieldLabel) {
        BigDecimal value = parseRequired(raw, fieldLabel);
        if (value.signum() < 0) {
            throw new IllegalArgumentException(fieldLabel + " не может быть отрицательным");
        }
        return value;
    }

    /** Parses a required decimal that must be non-zero (positive or negative). */
    public static BigDecimal parseNonZero(String raw, String fieldLabel) {
        BigDecimal value = parseRequired(raw, fieldLabel);
        if (value.signum() == 0) {
            throw new IllegalArgumentException(fieldLabel + " не может быть равным 0");
        }
        return value;
    }

    /**
     * Soft parse for inline table editors: blank → empty optional semantics via {@code null};
     * invalid or non-positive → {@code null}.
     */
    public static BigDecimal tryParsePositive(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(normalizeDecimalText(raw.trim()));
            if (value.signum() <= 0) {
                return null;
            }
            return value;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static String normalizeDecimalText(String value) {
        return value.replace(',', '.');
    }
}

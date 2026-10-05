package com.tmp.warehouse.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Fixed warehouse unit-of-measure reference.
 *
 * <p>MaterialReference stores only canonical codes from this list. Quantity is always kept in the
 * selected unit — no conversion between units is performed.
 */
public enum UnitOfMeasure {
    PIECES("шт."),
    METERS("м."),
    SQUARE_METERS("кв.м."),
    SET("компл."),
    LITERS("л."),
    GRAMS("гр."),
    KILOGRAMS("кг.");

    private final String code;

    UnitOfMeasure(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /** Canonical codes for UI ComboBox / API exposure. */
    public static List<String> codes() {
        return Arrays.stream(values()).map(UnitOfMeasure::code).toList();
    }

    /**
     * Resolves user/API input to a canonical code.
     *
     * @throws IllegalArgumentException when blank or unsupported
     */
    public static String requireCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("unitOfMeasure must not be blank");
        }
        return resolveCanonical(raw)
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Unsupported unitOfMeasure: " + raw.trim()));
    }

    /**
     * Persisted value: empty string allowed for legacy migrated materials; otherwise canonical.
     *
     * @throws IllegalArgumentException when non-empty and unsupported
     */
    public static String requirePersistedOrLegacyEmpty(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return requireCode(raw);
    }

    /** Natural-key normalization: blank stays empty; otherwise canonical. */
    public static String normalizeForKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return requireCode(raw);
    }

    /**
     * Comparison-time equality using the Warehouse canonical UoM rule.
     *
     * <p>When both sides resolve to a Warehouse canonical code, those codes are compared. When
     * either side is unsupported, falls back to exact trimmed equality so legacy/test non-catalog
     * units still match themselves without inventing fuzzy aliases.
     */
    public static boolean equalForKey(String left, String right) {
        Optional<String> leftCanonical = tryCanonicalKey(left);
        Optional<String> rightCanonical = tryCanonicalKey(right);
        if (leftCanonical.isPresent() && rightCanonical.isPresent()) {
            return leftCanonical.get().equals(rightCanonical.get());
        }
        String leftTrim = left == null ? "" : left.trim();
        String rightTrim = right == null ? "" : right.trim();
        return leftTrim.equals(rightTrim);
    }

    public static boolean isCanonical(String raw) {
        if (raw == null) {
            return false;
        }
        for (UnitOfMeasure unit : values()) {
            if (unit.code.equals(raw)) {
                return true;
            }
        }
        return false;
    }

    private static Optional<String> tryCanonicalKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.of("");
        }
        return resolveCanonical(raw);
    }

    private static Optional<String> resolveCanonical(String raw) {
        String key = normalizeKey(raw);
        for (UnitOfMeasure unit : values()) {
            if (normalizeKey(unit.code).equals(key)) {
                return Optional.of(unit.code);
            }
        }
        return switch (key) {
            case "шт", "штука", "штуки", "штук" -> Optional.of(PIECES.code);
            case "м", "метр", "метры", "метров" -> Optional.of(METERS.code);
            case "квм", "кв.м", "м2", "м²" -> Optional.of(SQUARE_METERS.code);
            case "компл", "комплект" -> Optional.of(SET.code);
            case "л", "литр", "литры", "литров" -> Optional.of(LITERS.code);
            case "гр", "г", "грамм", "граммы" -> Optional.of(GRAMS.code);
            case "кг", "килограмм", "килограммы" -> Optional.of(KILOGRAMS.code);
            default -> Optional.empty();
        };
    }

    private static String normalizeKey(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT).replace(" ", "");
    }
}

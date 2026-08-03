package com.tmp.ui.shell.order;

import java.math.BigDecimal;

/**
 * Shared local UI validation for product quantity ({@code OrderedQuantity}) fields.
 *
 * <p>Does not introduce a second business semantics of quantity; mirrors the domain expectation of
 * a positive whole number before calling application/document services. Whole-number checks use
 * mathematical integrity (fractional part is zero), not {@link BigDecimal#scale()}.
 */
public final class ProductQuantityUiValidation {

    private ProductQuantityUiValidation() {}

    public static void requireValidProductQuantity(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Количество изделий обязательно для заполнения.");
        }
        String normalized = raw.trim().replace(',', '.');
        final BigDecimal value;
        try {
            value = new BigDecimal(normalized);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Количество изделий должно быть числом.");
        }
        if (value.signum() <= 0 || !isWholeNumber(value)) {
            throw new IllegalArgumentException(
                    "Количество изделий должно быть целым числом больше нуля.");
        }
    }

    /**
     * A value is whole when its fractional part is zero (e.g. {@code 8}, {@code 8.0}, {@code
     * 8.000000}).
     */
    static boolean isWholeNumber(BigDecimal value) {
        return value.stripTrailingZeros().scale() <= 0;
    }
}

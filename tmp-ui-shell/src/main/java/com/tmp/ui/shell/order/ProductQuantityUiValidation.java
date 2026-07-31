package com.tmp.ui.shell.order;

import java.math.BigDecimal;

/**
 * Shared local UI validation for product quantity ({@code OrderedQuantity}) fields.
 *
 * <p>Does not introduce a second business semantics of quantity; mirrors the domain expectation of
 * a positive whole number before calling application/document services.
 */
public final class ProductQuantityUiValidation {

    private ProductQuantityUiValidation() {}

    public static void requireValidProductQuantity(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Количество изделий обязательно для заполнения.");
        }
        String trimmed = raw.trim();
        if (!trimmed.matches("-?\\d+")) {
            if (trimmed.matches("-?\\d+[\\.,]\\d+")) {
                throw new IllegalArgumentException(
                        "Количество изделий должно быть целым числом больше нуля.");
            }
            throw new IllegalArgumentException("Количество изделий должно быть числом.");
        }
        BigDecimal value = new BigDecimal(trimmed);
        if (value.scale() > 0) {
            throw new IllegalArgumentException(
                    "Количество изделий должно быть целым числом больше нуля.");
        }
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Количество изделий должно быть целым числом больше нуля.");
        }
    }
}

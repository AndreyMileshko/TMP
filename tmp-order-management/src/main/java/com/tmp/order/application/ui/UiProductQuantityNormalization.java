package com.tmp.order.application.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Application-layer normalization of product quantity values exposed to / accepted from desktop UI.
 *
 * <p>Does not change domain {@code OrderedQuantity} semantics: mathematically whole positives are
 * projected to scale {@code 0} so NUMERIC(19,6) leftovers such as {@code 8.000000} do not appear as
 * fractional text in editors.
 */
final class UiProductQuantityNormalization {

    private UiProductQuantityNormalization() {}

    static BigDecimal forUiContract(BigDecimal value) {
        if (value == null) {
            return null;
        }
        if (isPositiveWhole(value)) {
            return value.setScale(0, RoundingMode.UNNECESSARY);
        }
        return value;
    }

    static BigDecimal parseForOrderedQuantity(String orderedQuantity) {
        Objects.requireNonNull(orderedQuantity, "orderedQuantity");
        String trimmed = orderedQuantity.trim().replace(',', '.');
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("orderedQuantity must not be blank");
        }
        final BigDecimal value;
        try {
            value = new BigDecimal(trimmed);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("orderedQuantity must be a number: " + trimmed, ex);
        }
        return forUiContract(value);
    }

    private static boolean isPositiveWhole(BigDecimal value) {
        return value.signum() > 0 && value.remainder(BigDecimal.ONE).signum() == 0;
    }
}

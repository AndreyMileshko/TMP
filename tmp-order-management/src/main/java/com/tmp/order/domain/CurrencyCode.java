package com.tmp.order.domain;

import java.util.Objects;

/**
 * Settlement currency code of a customer order (Specification §5.1). Stored as a non-blank code;
 * ISO catalogue validation is out of Stage 5 domain scope.
 */
public final class CurrencyCode {

    private final String value;

    private CurrencyCode(String value) {
        this.value = value;
    }

    public static CurrencyCode of(String value) {
        Objects.requireNonNull(value, "value");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Currency code must not be blank");
        }
        return new CurrencyCode(trimmed);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CurrencyCode that)) {
            return false;
        }
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}

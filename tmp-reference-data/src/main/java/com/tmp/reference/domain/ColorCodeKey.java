package com.tmp.reference.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * Case-insensitive uniqueness key for a TMP color catalog code (TMP-SPEC-015 section 5.1).
 *
 * <p>Computed only in Java with {@link Locale#ROOT}. PostgreSQL {@code upper()}/{@code lower()}
 * must not be used for this key.
 */
public final class ColorCodeKey {

    private final String value;

    private ColorCodeKey(String value) {
        this.value = value;
    }

    /**
     * Builds the normalized key from a stored catalog {@code code}.
     *
     * @param code catalog code in its preserved spelling (not null)
     * @return key equal to {@code code.toUpperCase(Locale.ROOT)}
     */
    public static ColorCodeKey of(String code) {
        Objects.requireNonNull(code, "code");
        return new ColorCodeKey(code.toUpperCase(Locale.ROOT));
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ColorCodeKey that)) {
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
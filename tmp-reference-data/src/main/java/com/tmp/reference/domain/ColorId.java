package com.tmp.reference.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Stable technical identity of a TMP catalog color (TMP-SPEC-015 section 5.1).
 *
 * <p>Not shown to end users; the visible identifier is the preserved color code.
 */
public final class ColorId {

    private final UUID value;

    private ColorId(UUID value) {
        this.value = value;
    }

    public static ColorId of(UUID value) {
        Objects.requireNonNull(value, "value");
        return new ColorId(value);
    }

    public static ColorId generate() {
        return new ColorId(UUID.randomUUID());
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ColorId that)) {
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
        return value.toString();
    }
}

package com.tmp.security.api;

import java.util.Objects;

/**
 * Immutable display name of a Security user. Carries no password or credential data.
 */
public final class DisplayName {

    private static final int MAX_LENGTH = 255;

    private final String value;

    private DisplayName(String value) {
        this.value = value;
    }

    public static DisplayName of(String value) {
        Objects.requireNonNull(value, "value");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Display name must not be blank");
        }
        if (trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Display name must not exceed " + MAX_LENGTH + " characters");
        }
        return new DisplayName(trimmed);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DisplayName that)) {
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

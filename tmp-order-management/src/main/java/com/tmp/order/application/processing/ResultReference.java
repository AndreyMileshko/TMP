package com.tmp.order.application.processing;

import java.util.Objects;
import java.util.Optional;

/**
 * Internal opaque reference to the business result of a posted document (Specification §16).
 *
 * <p>Never returned from {@code DocumentProcessor.onPost()} and not part of any public API.
 */
public final class ResultReference {

    private final String value;

    private ResultReference(String value) {
        this.value = value;
    }

    public static ResultReference of(String value) {
        Objects.requireNonNull(value, "value");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("result reference must not be blank");
        }
        return new ResultReference(trimmed);
    }

    public static Optional<ResultReference> optional(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(of(value));
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ResultReference that)) {
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

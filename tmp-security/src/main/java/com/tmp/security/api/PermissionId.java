package com.tmp.security.api;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable identifier of a permission declared by a Capability.
 *
 * <p>Format: {@code <area>.<resource>.<action>} — three lowercase segments.
 * Carries no password or credential data. The identifier is immutable after construction;
 * registration-time immutability is enforced by the application layer.
 */
public final class PermissionId {

    private static final Pattern FORMAT =
            Pattern.compile("^[a-z][a-z0-9-]*\\.[a-z][a-z0-9-]*\\.[a-z][a-z0-9-]*$");

    private final String value;

    private PermissionId(String value) {
        this.value = value;
    }

    public static PermissionId of(String value) {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Permission id must not be blank");
        }
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Permission id must match <area>.<resource>.<action> "
                            + "(three lowercase segments): " + value);
        }
        return new PermissionId(value);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PermissionId that)) {
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

package com.tmp.order.domain;

/**
 * Schema version of a capability-owned typed document payload (Specification §11.2).
 *
 * <p>Internal to Order Management: the typed payload is not exposed to other Capabilities. Schema
 * versions start at {@code 1} and increase as the payload model evolves.
 */
public final class PayloadSchemaVersion {

    private static final int INITIAL_VALUE = 1;

    private final int value;

    private PayloadSchemaVersion(int value) {
        this.value = value;
    }

    /**
     * Wraps an existing schema version.
     *
     * @param value schema version, must be {@code >= 1}
     * @return the schema version
     * @throws IllegalArgumentException if {@code value < 1}
     */
    public static PayloadSchemaVersion of(int value) {
        if (value < INITIAL_VALUE) {
            throw new IllegalArgumentException(
                    "Payload schema version must be >= " + INITIAL_VALUE + ": " + value);
        }
        return new PayloadSchemaVersion(value);
    }

    /**
     * Returns the initial schema version ({@code 1}).
     *
     * @return the initial schema version
     */
    public static PayloadSchemaVersion initial() {
        return new PayloadSchemaVersion(INITIAL_VALUE);
    }

    public int value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PayloadSchemaVersion that)) {
            return false;
        }
        return value == that.value;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(value);
    }

    @Override
    public String toString() {
        return "v" + value;
    }
}

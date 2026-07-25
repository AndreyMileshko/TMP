package com.tmp.order.application.payload;

import java.util.Objects;
import java.util.UUID;

/**
 * Typed identity of a platform document payload, equal to Document Engine {@code DocumentMetadata.id()}
 * (Specification §11.2, ADR-028).
 *
 * <p>Internal to Order Management application layer — not part of the public Query API.
 */
public final class DocumentId {

    private final UUID value;

    private DocumentId(UUID value) {
        this.value = value;
    }

    public static DocumentId of(UUID value) {
        Objects.requireNonNull(value, "value");
        return new DocumentId(value);
    }

    public static DocumentId generate() {
        return new DocumentId(UUID.randomUUID());
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DocumentId that)) {
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

package com.tmp.production.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Production-owned identity of a Material Transfer Template.
 *
 * <p>Not a Warehouse Transfer ID, Document Engine document ID, or Order ID.
 */
public final class MaterialTransferTemplateId {

    private final UUID value;

    private MaterialTransferTemplateId(UUID value) {
        this.value = value;
    }

    public static MaterialTransferTemplateId of(UUID value) {
        Objects.requireNonNull(value, "value");
        return new MaterialTransferTemplateId(value);
    }

    public static MaterialTransferTemplateId generate() {
        return new MaterialTransferTemplateId(UUID.randomUUID());
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MaterialTransferTemplateId that)) {
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

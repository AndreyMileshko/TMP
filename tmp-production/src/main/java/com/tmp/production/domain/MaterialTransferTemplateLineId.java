package com.tmp.production.domain;

import java.util.Objects;
import java.util.UUID;

/** Production-owned identity of one Material Transfer Template line. */
public final class MaterialTransferTemplateLineId {

    private final UUID value;

    private MaterialTransferTemplateLineId(UUID value) {
        this.value = value;
    }

    public static MaterialTransferTemplateLineId of(UUID value) {
        Objects.requireNonNull(value, "value");
        return new MaterialTransferTemplateLineId(value);
    }

    public static MaterialTransferTemplateLineId generate() {
        return new MaterialTransferTemplateLineId(UUID.randomUUID());
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MaterialTransferTemplateLineId that)) {
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

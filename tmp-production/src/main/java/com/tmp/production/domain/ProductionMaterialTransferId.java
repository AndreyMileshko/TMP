package com.tmp.production.domain;

import java.util.Objects;
import java.util.UUID;

/** Production-owned identity of one logical material transfer confirmation. */
public final class ProductionMaterialTransferId {

    private final UUID value;

    private ProductionMaterialTransferId(UUID value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    public static ProductionMaterialTransferId of(UUID value) {
        return new ProductionMaterialTransferId(value);
    }

    public static ProductionMaterialTransferId generate() {
        return new ProductionMaterialTransferId(UUID.randomUUID());
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ProductionMaterialTransferId other)) {
            return false;
        }
        return value.equals(other.value);
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

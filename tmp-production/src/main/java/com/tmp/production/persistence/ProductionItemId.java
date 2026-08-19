package com.tmp.production.persistence;

import java.util.Objects;
import java.util.UUID;

/**
 * Internal surrogate identifier for a persisted {@code production.production_item_states} row.
 *
 * <p>Does not replace {@link com.tmp.production.domain.SourceOrderItemId}; business identity remains
 * {@code SourceOrderId + SourceOrderItemId + SpecificationId}.
 */
public final class ProductionItemId {

    private final UUID value;

    private ProductionItemId(UUID value) {
        this.value = value;
    }

    public static ProductionItemId of(UUID value) {
        Objects.requireNonNull(value, "value");
        return new ProductionItemId(value);
    }

    public static ProductionItemId generate() {
        return new ProductionItemId(UUID.randomUUID());
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProductionItemId that)) {
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

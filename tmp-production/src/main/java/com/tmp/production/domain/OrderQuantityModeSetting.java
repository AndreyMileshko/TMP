package com.tmp.production.domain;

import java.util.Objects;

/**
 * Production-owned quantity mode setting of one order.
 *
 * <p>Not a Production Order, document or state. {@code version == 0} means no stored setting yet:
 * the order uses {@link ProductionQuantityMode#defaultMode()}.
 */
public record OrderQuantityModeSetting(
        SourceOrderId sourceOrderId, ProductionQuantityMode quantityMode, long version) {

    public OrderQuantityModeSetting {
        Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        Objects.requireNonNull(quantityMode, "quantityMode");
        if (version < 0L) {
            throw new IllegalArgumentException("version must be >= 0");
        }
    }

    public static OrderQuantityModeSetting defaultFor(SourceOrderId sourceOrderId) {
        return new OrderQuantityModeSetting(sourceOrderId, ProductionQuantityMode.defaultMode(), 0L);
    }
}

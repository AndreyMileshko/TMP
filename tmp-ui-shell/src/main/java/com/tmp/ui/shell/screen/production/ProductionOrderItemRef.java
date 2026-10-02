package com.tmp.ui.shell.screen.production;

import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

/**
 * Stable business identity of a selected Production order item. Not a display index or TreeItem
 * reference.
 */
public record ProductionOrderItemRef(UUID sourceOrderId, UUID sourceOrderItemId)
        implements Comparable<ProductionOrderItemRef> {

    public static final Comparator<ProductionOrderItemRef> DETERMINISTIC_ORDER =
            Comparator.comparing(ProductionOrderItemRef::sourceOrderId)
                    .thenComparing(ProductionOrderItemRef::sourceOrderItemId);

    public ProductionOrderItemRef {
        Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
    }

    @Override
    public int compareTo(ProductionOrderItemRef other) {
        return DETERMINISTIC_ORDER.compare(this, other);
    }
}

package com.tmp.warehouse.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Pure attribution / headroom rules for optional resupply waiver (ADR-038 amendment).
 *
 * <p>Preserves {@code received + waived <= required} even when other ACTIVE Transfers remain linked
 * to the same Demand line by reserving their {@code linked_quantity} from waiver headroom.
 */
public final class WarehouseDemandResupplyWaiverRules {

    private WarehouseDemandResupplyWaiverRules() {}

    public static BigDecimal computeWaiveQuantity(
            BigDecimal linkedQuantity,
            BigDecimal requiredQuantity,
            BigDecimal receivedQuantity,
            BigDecimal alreadyWaived,
            BigDecimal otherActiveLinkedQuantity) {
        Objects.requireNonNull(linkedQuantity, "linkedQuantity");
        Objects.requireNonNull(requiredQuantity, "requiredQuantity");
        Objects.requireNonNull(receivedQuantity, "receivedQuantity");
        Objects.requireNonNull(alreadyWaived, "alreadyWaived");
        Objects.requireNonNull(otherActiveLinkedQuantity, "otherActiveLinkedQuantity");
        if (linkedQuantity.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal headroom =
                requiredQuantity
                        .subtract(receivedQuantity)
                        .subtract(alreadyWaived)
                        .subtract(otherActiveLinkedQuantity);
        if (headroom.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return linkedQuantity.min(headroom);
    }
}
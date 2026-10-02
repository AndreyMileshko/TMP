package com.tmp.ui.shell.screen.production;

import com.tmp.production.api.ProductionQueryApi.OrderProductionViewStatus;

/**
 * Pure presentation policy for Production Order Card actions. Quantity Mode editing is separate and
 * state-independent.
 */
public final class ProductionActionPolicy {

    private ProductionActionPolicy() {}

    public record Decision(boolean accept) {

        public static Decision none() {
            return new Decision(false);
        }
    }

    public static Decision evaluate(
            boolean orderSelected, OrderProductionViewStatus status, boolean acceptPermission) {
        if (!orderSelected || status == null) {
            return Decision.none();
        }
        if (status == OrderProductionViewStatus.NOT_ACCEPTED && acceptPermission) {
            return new Decision(true);
        }
        return Decision.none();
    }
}

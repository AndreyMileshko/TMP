package com.tmp.ui.shell.screen.production;

import com.tmp.production.api.ProductionQueryApi.OrderProductionViewStatus;

/**
 * Pure presentation policy for Production Order Card actions. Quantity Mode editing is separate and
 * state-independent.
 */
public final class ProductionActionPolicy {

    private ProductionActionPolicy() {}

    public record Decision(boolean accept, boolean cancel) {

        public static Decision none() {
            return new Decision(false, false);
        }
    }

    public static Decision evaluate(
            boolean orderSelected,
            OrderProductionViewStatus status,
            boolean acceptPermission,
            boolean cancelPermission) {
        if (!orderSelected || status == null) {
            return Decision.none();
        }
        boolean accept =
                status == OrderProductionViewStatus.NOT_ACCEPTED && acceptPermission;
        // Backend allows cancellation only when Order Production View is IN_PRODUCTION.
        boolean cancel =
                status == OrderProductionViewStatus.IN_PRODUCTION && cancelPermission;
        return new Decision(accept, cancel);
    }
}

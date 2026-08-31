package com.tmp.ui.shell.screen.production;

import com.tmp.production.api.ProductionQueryApi.OrderProductionViewStatus;
import java.util.Objects;

/**
 * Pure presentation policy for Production workbench mutation buttons. No API calls.
 */
public final class ProductionActionPolicy {

    private ProductionActionPolicy() {}

    public record Permissions(
            boolean accept,
            boolean check,
            boolean transfer,
            boolean receipt,
            boolean release,
            boolean cancel) {}

    public record Decision(
            boolean accept,
            boolean check,
            boolean transfer,
            boolean receipt,
            boolean release,
            boolean cancel) {

        public static Decision none() {
            return new Decision(false, false, false, false, false, false);
        }
    }

    /**
     * @param transferReceivable true when selected logical transfer has at least one Warehouse SENT
     *     ref and no DRAFT refs (Warehouse lifecycle)
     */
    public static Decision evaluate(
            boolean orderSelected,
            OrderProductionViewStatus status,
            Permissions permissions,
            boolean transferReceivable) {
        Objects.requireNonNull(permissions, "permissions");
        if (!orderSelected || status == null) {
            return Decision.none();
        }
        return switch (status) {
            case NOT_ACCEPTED -> new Decision(
                    permissions.accept(), false, false, false, false, false);
            case IN_PRODUCTION -> new Decision(
                    false,
                    permissions.check(),
                    permissions.transfer(),
                    permissions.receipt() && transferReceivable,
                    permissions.release(),
                    permissions.cancel());
            case MANUFACTURED, CANCELLED -> Decision.none();
        };
    }
}

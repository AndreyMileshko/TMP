package com.tmp.ui.shell.screen.production;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.production.api.ProductionQueryApi.OrderProductionViewStatus;
import org.junit.jupiter.api.Test;

class ProductionActionPolicyTest {

    @Test
    void withoutOrderAllActionsDisabled() {
        ProductionActionPolicy.Decision decision =
                ProductionActionPolicy.evaluate(
                        false, OrderProductionViewStatus.NOT_ACCEPTED, true, true);
        assertFalse(decision.accept());
        assertFalse(decision.cancel());
    }

    @Test
    void notAcceptedEnablesAcceptWhenPermitted() {
        ProductionActionPolicy.Decision withPerm =
                ProductionActionPolicy.evaluate(
                        true, OrderProductionViewStatus.NOT_ACCEPTED, true, true);
        assertTrue(withPerm.accept());
        assertFalse(withPerm.cancel());

        ProductionActionPolicy.Decision withoutPerm =
                ProductionActionPolicy.evaluate(
                        true, OrderProductionViewStatus.NOT_ACCEPTED, false, true);
        assertFalse(withoutPerm.accept());
        assertFalse(withoutPerm.cancel());
    }

    @Test
    void inProductionEnablesCancelWhenPermitted() {
        ProductionActionPolicy.Decision withPerm =
                ProductionActionPolicy.evaluate(
                        true, OrderProductionViewStatus.IN_PRODUCTION, true, true);
        assertFalse(withPerm.accept());
        assertTrue(withPerm.cancel());

        ProductionActionPolicy.Decision withoutPerm =
                ProductionActionPolicy.evaluate(
                        true, OrderProductionViewStatus.IN_PRODUCTION, true, false);
        assertFalse(withoutPerm.accept());
        assertFalse(withoutPerm.cancel());
    }

    @Test
    void manufacturedAndCancelledDisableAcceptAndCancel() {
        assertFalse(
                ProductionActionPolicy.evaluate(
                                true, OrderProductionViewStatus.MANUFACTURED, true, true)
                        .accept());
        assertFalse(
                ProductionActionPolicy.evaluate(
                                true, OrderProductionViewStatus.MANUFACTURED, true, true)
                        .cancel());
        assertFalse(
                ProductionActionPolicy.evaluate(
                                true, OrderProductionViewStatus.CANCELLED, true, true)
                        .accept());
        assertFalse(
                ProductionActionPolicy.evaluate(
                                true, OrderProductionViewStatus.CANCELLED, true, true)
                        .cancel());
    }

    @Test
    void nullStatusDisablesActions() {
        ProductionActionPolicy.Decision decision =
                ProductionActionPolicy.evaluate(true, null, true, true);
        assertFalse(decision.accept());
        assertFalse(decision.cancel());
    }
}

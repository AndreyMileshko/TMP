package com.tmp.ui.shell.screen.production;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.production.api.ProductionQueryApi.OrderProductionViewStatus;
import org.junit.jupiter.api.Test;

class ProductionActionPolicyTest {

    @Test
    void withoutOrderAcceptDisabled() {
        ProductionActionPolicy.Decision decision =
                ProductionActionPolicy.evaluate(
                        false, OrderProductionViewStatus.NOT_ACCEPTED, true);
        assertFalse(decision.accept());
    }

    @Test
    void notAcceptedEnablesAcceptWhenPermitted() {
        ProductionActionPolicy.Decision withPerm =
                ProductionActionPolicy.evaluate(
                        true, OrderProductionViewStatus.NOT_ACCEPTED, true);
        assertTrue(withPerm.accept());

        ProductionActionPolicy.Decision withoutPerm =
                ProductionActionPolicy.evaluate(
                        true, OrderProductionViewStatus.NOT_ACCEPTED, false);
        assertFalse(withoutPerm.accept());
    }

    @Test
    void inProductionDisablesAcceptEvenWithPermission() {
        ProductionActionPolicy.Decision decision =
                ProductionActionPolicy.evaluate(
                        true, OrderProductionViewStatus.IN_PRODUCTION, true);
        assertFalse(decision.accept());
    }

    @Test
    void manufacturedAndCancelledDisableAccept() {
        assertFalse(
                ProductionActionPolicy.evaluate(
                                true, OrderProductionViewStatus.MANUFACTURED, true)
                        .accept());
        assertFalse(
                ProductionActionPolicy.evaluate(
                                true, OrderProductionViewStatus.CANCELLED, true)
                        .accept());
    }

    @Test
    void nullStatusDisablesAccept() {
        assertFalse(ProductionActionPolicy.evaluate(true, null, true).accept());
    }
}

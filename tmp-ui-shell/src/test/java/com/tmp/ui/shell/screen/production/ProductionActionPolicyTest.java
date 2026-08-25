package com.tmp.ui.shell.screen.production;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.production.api.ProductionQueryApi.OrderProductionViewStatus;
import org.junit.jupiter.api.Test;

class ProductionActionPolicyTest {

    private static final ProductionActionPolicy.Permissions ALL =
            new ProductionActionPolicy.Permissions(true, true, true, true, true, true);
    private static final ProductionActionPolicy.Permissions NONE =
            new ProductionActionPolicy.Permissions(false, false, false, false, false, false);

    @Test
    void withoutOrderAllDisabled() {
        ProductionActionPolicy.Decision decision =
                ProductionActionPolicy.evaluate(
                        false, OrderProductionViewStatus.IN_PRODUCTION, ALL, true);
        assertFalse(decision.accept());
        assertFalse(decision.check());
        assertFalse(decision.transfer());
        assertFalse(decision.receipt());
        assertFalse(decision.release());
        assertFalse(decision.cancel());
    }

    @Test
    void notAcceptedOnlyAcceptWhenPermitted() {
        ProductionActionPolicy.Decision withPerm =
                ProductionActionPolicy.evaluate(
                        true, OrderProductionViewStatus.NOT_ACCEPTED, ALL, false);
        assertTrue(withPerm.accept());
        assertFalse(withPerm.check());
        assertFalse(withPerm.transfer());
        assertFalse(withPerm.receipt());
        assertFalse(withPerm.release());
        assertFalse(withPerm.cancel());

        ProductionActionPolicy.Decision withoutPerm =
                ProductionActionPolicy.evaluate(
                        true, OrderProductionViewStatus.NOT_ACCEPTED, NONE, false);
        assertFalse(withoutPerm.accept());
    }

    @Test
    void inProductionEnablesPermissionBasedMutationsExceptAccept() {
        ProductionActionPolicy.Decision decision =
                ProductionActionPolicy.evaluate(
                        true, OrderProductionViewStatus.IN_PRODUCTION, ALL, true);
        assertFalse(decision.accept());
        assertTrue(decision.check());
        assertTrue(decision.transfer());
        assertTrue(decision.receipt());
        assertTrue(decision.release());
        assertTrue(decision.cancel());
    }

    @Test
    void inProductionReceiptRequiresApplicableTransfer() {
        ProductionActionPolicy.Decision withoutTransfer =
                ProductionActionPolicy.evaluate(
                        true, OrderProductionViewStatus.IN_PRODUCTION, ALL, false);
        assertFalse(withoutTransfer.receipt());
        assertTrue(withoutTransfer.check());
    }

    @Test
    void inProductionRespectsIndividualPermissions() {
        ProductionActionPolicy.Permissions onlyCheck =
                new ProductionActionPolicy.Permissions(false, true, false, false, false, false);
        ProductionActionPolicy.Decision decision =
                ProductionActionPolicy.evaluate(
                        true, OrderProductionViewStatus.IN_PRODUCTION, onlyCheck, true);
        assertTrue(decision.check());
        assertFalse(decision.transfer());
        assertFalse(decision.receipt());
        assertFalse(decision.release());
        assertFalse(decision.cancel());
    }

    @Test
    void manufacturedDisablesAllMutations() {
        ProductionActionPolicy.Decision decision =
                ProductionActionPolicy.evaluate(
                        true, OrderProductionViewStatus.MANUFACTURED, ALL, true);
        assertFalse(decision.accept());
        assertFalse(decision.check());
        assertFalse(decision.transfer());
        assertFalse(decision.receipt());
        assertFalse(decision.release());
        assertFalse(decision.cancel());
    }

    @Test
    void cancelledDisablesAllMutations() {
        ProductionActionPolicy.Decision decision =
                ProductionActionPolicy.evaluate(
                        true, OrderProductionViewStatus.CANCELLED, ALL, true);
        assertFalse(decision.accept());
        assertFalse(decision.check());
        assertFalse(decision.transfer());
        assertFalse(decision.receipt());
        assertFalse(decision.release());
        assertFalse(decision.cancel());
    }
}

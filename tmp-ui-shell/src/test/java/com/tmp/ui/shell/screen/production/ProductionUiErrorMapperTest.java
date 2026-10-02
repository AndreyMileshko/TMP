package com.tmp.ui.shell.screen.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductionUiErrorMapperTest {

    @Test
    void mapsExactOrderNotFound() {
        assertEquals(
                ProductionUiErrorMapper.ORDER_NOT_FOUND,
                ProductionUiErrorMapper.text(new IllegalArgumentException("Order not found")));
    }

    @Test
    void doesNotMapDestinationWarehouseNotFoundAsOrderNotFound() {
        String mapped =
                ProductionUiErrorMapper.text(
                        new IllegalStateException(
                                "Configured destination warehouse not found: "
                                        + UUID.randomUUID()));
        assertEquals(ProductionUiErrorMapper.TECHNICAL_FAILURE, mapped);
        assertNotEquals(ProductionUiErrorMapper.ORDER_NOT_FOUND, mapped);
    }

    @Test
    void mapsGenericIllegalStateAsTechnicalFailure() {
        String mapped =
                ProductionUiErrorMapper.text(
                        new IllegalStateException(
                                "Не назначен склад производства. Укажите его в Настройки склада → Склады."));
        assertEquals(ProductionUiErrorMapper.TECHNICAL_FAILURE, mapped);
        assertNotEquals(ProductionUiErrorMapper.ORDER_NOT_FOUND, mapped);
    }

    @Test
    void mapsQuantityModeOptimisticLockConflict() {
        RuntimeException error =
                new ProductionWorkbenchUiTestSupport.OrderQuantityModeOptimisticLockStubException(
                        UUID.randomUUID(), 0L);
        assertEquals(ProductionUiErrorMapper.QUANTITY_MODE_CONFLICT, ProductionUiErrorMapper.text(error));
        assertTrue(ProductionUiErrorMapper.isQuantityModeConflict(error));
    }

    @Test
    void mapsAcceptConflict() {
        RuntimeException error = new ProductionLaunchConflictStubException();
        assertEquals(ProductionUiErrorMapper.ACCEPT_CONFLICT, ProductionUiErrorMapper.text(error));
        assertTrue(ProductionUiErrorMapper.isAcceptConflict(error));
    }

    @Test
    void exposesCardLoadFailedConstantForOrderCardOperations() {
        assertEquals(
                "Не удалось загрузить данные заказа. Повторите попытку.",
                ProductionUiErrorMapper.CARD_LOAD_FAILED);
    }

    private static final class ProductionLaunchConflictStubException extends RuntimeException {
        ProductionLaunchConflictStubException() {
            super("ProductionLaunchConflict: already launched");
        }
    }
}

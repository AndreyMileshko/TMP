package com.tmp.ui.shell.screen.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.NoSuchElementException;
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
        assertEquals(ProductionUiErrorMapper.DESTINATION_WAREHOUSE_INVALID, mapped);
        assertNotEquals(ProductionUiErrorMapper.ORDER_NOT_FOUND, mapped);
    }

    @Test
    void mapsProductionWarehouseNotAssignedMessage() {
        String mapped =
                ProductionUiErrorMapper.text(
                        new IllegalStateException(
                                "Не назначен склад производства. Укажите его в Настройки склада → Склады."));
        assertEquals(ProductionUiErrorMapper.DESTINATION_WAREHOUSE_INVALID, mapped);
        assertNotEquals(ProductionUiErrorMapper.ORDER_NOT_FOUND, mapped);
    }
}

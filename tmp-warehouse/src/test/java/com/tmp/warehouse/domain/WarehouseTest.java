package com.tmp.warehouse.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WarehouseTest {

    @Test
    void createRequiresCodeAndName() {
        Warehouse warehouse =
                Warehouse.create(WarehouseId.generate(), "WH-01", "Main warehouse");
        assertEquals("WH-01", warehouse.code());
        assertEquals("Main warehouse", warehouse.name());
        assertTrue(warehouse.active());
    }

    @Test
    void blankCodeIsRejected() {
        WarehouseId id = WarehouseId.generate();
        assertThrows(IllegalArgumentException.class, () -> Warehouse.create(id, "  ", "Name"));
    }

    @Test
    void warehouseMayBeInactive() {
        Warehouse inactive =
                Warehouse.of(WarehouseId.generate(), "WH-02", "Spare", false);
        assertFalse(inactive.active());
        Warehouse active = inactive.activate();
        assertTrue(active.active());
        assertFalse(active.deactivate().active());
    }
}

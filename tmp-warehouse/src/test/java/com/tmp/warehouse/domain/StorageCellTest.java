package com.tmp.warehouse.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StorageCellTest {

    @Test
    void cellBelongsToWarehouseAndRequiresCode() {
        WarehouseId warehouseId = WarehouseId.generate();
        StorageCell cell =
                StorageCell.create(StorageCellId.generate(), warehouseId, "A-01");
        assertEquals("A-01", cell.code());
        assertTrue(cell.belongsTo(warehouseId));
        assertTrue(cell.active());
    }

    @Test
    void blankCodeIsRejected() {
        WarehouseId warehouseId = WarehouseId.generate();
        StorageCellId id = StorageCellId.generate();
        assertThrows(
                IllegalArgumentException.class,
                () -> StorageCell.create(id, warehouseId, " "));
    }

    @Test
    void nullWarehouseReferenceIsRejected() {
        StorageCellId id = StorageCellId.generate();
        assertThrows(NullPointerException.class, () -> StorageCell.create(id, null, "A-01"));
    }

    @Test
    void belongsToRejectsOtherWarehouse() {
        StorageCell cell =
                StorageCell.create(
                        StorageCellId.generate(), WarehouseId.generate(), "B-02");
        assertFalse(cell.belongsTo(WarehouseId.generate()));
    }
}

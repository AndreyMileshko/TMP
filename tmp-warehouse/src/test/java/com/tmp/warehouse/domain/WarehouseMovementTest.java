package com.tmp.warehouse.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class WarehouseMovementTest {

    @Test
    void movementIsImmutableHistoryRecord() {
        WarehouseMovementId id = WarehouseMovementId.generate();
        WarehouseOperationId operationId = WarehouseOperationId.generate();
        WarehouseId warehouseId = WarehouseId.generate();
        StorageCellId cellId = StorageCellId.generate();
        MaterialReference material = MaterialReference.of("VEKA-103.211");
        Instant occurredAt = Instant.parse("2026-08-07T06:00:00Z");

        WarehouseMovement movement =
                WarehouseMovement.record(
                        id,
                        operationId,
                        warehouseId,
                        cellId,
                        material,
                        StockState.AVAILABLE,
                        StockState.IN_TRANSIT,
                        StockQuantity.of(10),
                        StockQuantity.of(4),
                        occurredAt);

        assertEquals(id, movement.id());
        assertEquals(operationId, movement.operationId());
        assertEquals(StockState.AVAILABLE, movement.previousState());
        assertEquals(StockState.IN_TRANSIT, movement.newState());
        assertEquals(StockQuantity.of(10), movement.previousQuantity());
        assertEquals(StockQuantity.of(4), movement.newQuantity());
        assertEquals(occurredAt, movement.occurredAt());
    }

    @Test
    void movementHasNoPublicMutationMethods() {
        for (Method method : WarehouseMovement.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            String name = method.getName();
            assertTrue(
                    !name.startsWith("set") && !name.startsWith("with"),
                    "Public mutation method must not exist: " + name);
        }
    }
}

package com.tmp.warehouse.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class WarehouseMovementTest {

    @Test
    void movementRecordsStockPositionReferenceOperationTypeDeltaAndCreatedAt() {
        WarehouseMovementId id = WarehouseMovementId.generate();
        StockPositionId stockPositionId = StockPositionId.generate();
        Instant createdAt = Instant.parse("2026-08-07T06:00:00Z");
        BigDecimal quantityDelta = new BigDecimal("2.500000");

        WarehouseMovement movement =
                WarehouseMovement.record(
                        id,
                        stockPositionId,
                        WarehouseOperationType.RECEIPT,
                        quantityDelta,
                        createdAt);

        assertEquals(id, movement.id());
        assertEquals(stockPositionId, movement.stockPositionId());
        assertEquals(WarehouseOperationType.RECEIPT, movement.operationType());
        assertEquals(0, movement.quantityDelta().compareTo(quantityDelta));
        assertEquals(createdAt, movement.createdAt());
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

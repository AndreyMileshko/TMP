package com.tmp.warehouse.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class StockPositionTest {

    @Test
    void stockPositionCreationHoldsWarehouseCellMaterialStateAndQuantity() {
        WarehouseId warehouseId = WarehouseId.generate();
        StorageCellId cellId = StorageCellId.generate();
        MaterialReference material = MaterialReference.legacyArticle("VEKA-103.211");
        StockPositionId id = StockPositionId.generate();
        StockPosition position =
                StockPosition.of(
                        id,
                        warehouseId,
                        cellId,
                        material,
                        StockState.AVAILABLE,
                        StockQuantity.of(10));
        assertEquals(id, position.id());
        assertEquals(warehouseId, position.warehouseId());
        assertEquals(cellId, position.storageCellId());
        assertEquals(material, position.material());
        assertEquals(StockState.AVAILABLE, position.stockState());
        assertEquals(StockQuantity.of(10), position.quantity());
        assertEquals(0L, position.version());
    }

    @Test
    void negativeQuantityIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> StockQuantity.of(BigDecimal.valueOf(-1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> StockPosition.of(
                        WarehouseId.generate(),
                        StorageCellId.generate(),
                        MaterialReference.legacyArticle("MAT-1"),
                        StockState.AVAILABLE,
                        StockQuantity.of(BigDecimal.valueOf(-5))));
    }

    @Test
    void stateChangeThroughOperationPathUpdatesStockState() {
        WarehouseOperation operation =
                WarehouseOperation.describe(
                        WarehouseOperationId.generate(),
                        WarehouseOperationType.ADJUSTMENT,
                        MaterialReference.legacyArticle("ALU-6060"),
                        WarehouseId.generate(),
                        StorageCellId.generate(),
                        StockQuantity.of(8));
        StockPosition initial = operation.createPosition(StockState.AVAILABLE);
        StockPosition blocked =
                operation.applyTo(initial, StockState.BLOCKED, StockQuantity.of(8));
        StockPosition inTransit =
                operation.applyTo(blocked, StockState.IN_TRANSIT, StockQuantity.of(8));

        assertEquals(StockState.AVAILABLE, initial.stockState());
        assertEquals(StockState.BLOCKED, blocked.stockState());
        assertEquals(StockState.IN_TRANSIT, inTransit.stockState());
        assertEquals(initial.id(), blocked.id());
        assertEquals(initial.id(), inTransit.id());
    }

    @Test
    void reservedStockStateDoesNotExist() {
        Set<String> names =
                Arrays.stream(StockState.values()).map(Enum::name).collect(Collectors.toSet());
        assertEquals(Set.of("AVAILABLE", "IN_TRANSIT", "BLOCKED"), names);
        assertTrue(!names.contains("RESERVED"));
    }

    @Test
    void stockPositionHasNoPublicMutationMethods() {
        for (Method method : StockPosition.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            String name = method.getName();
            assertTrue(
                    !name.startsWith("set")
                            && !name.startsWith("with")
                            && !name.equals("applyChange"),
                    "Public mutation method must not exist: " + name);
        }
    }
}

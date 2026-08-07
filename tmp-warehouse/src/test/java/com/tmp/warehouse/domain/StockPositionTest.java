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
    void stockPositionHoldsWarehouseCellMaterialStateAndQuantity() {
        WarehouseId warehouseId = WarehouseId.generate();
        StorageCellId cellId = StorageCellId.generate();
        MaterialReference material = MaterialReference.of("VEKA-103.211");
        StockPosition position =
                StockPosition.of(
                        warehouseId,
                        cellId,
                        material,
                        StockState.AVAILABLE,
                        StockQuantity.of(10));
        assertEquals(warehouseId, position.warehouseId());
        assertEquals(cellId, position.storageCellId());
        assertEquals(material, position.material());
        assertEquals(StockState.AVAILABLE, position.stockState());
        assertEquals(StockQuantity.of(10), position.quantity());
    }

    @Test
    void negativeQuantityIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> StockQuantity.of(BigDecimal.valueOf(-1)));
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

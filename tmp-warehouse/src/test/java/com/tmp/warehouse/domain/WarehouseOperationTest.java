package com.tmp.warehouse.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class WarehouseOperationTest {

    @Test
    void describeCreatesOperationWithoutExecutionSideEffects() {
        WarehouseOperation operation = sampleOperation();
        assertEquals(WarehouseOperationType.RECEIPT, operation.type());
        assertEquals(StockQuantity.of(5), operation.quantity());
    }

    @Test
    void applyToChangesStockOnlyThroughOperationPath() {
        WarehouseOperation operation = sampleOperation();
        StockPosition initial = operation.createPosition(StockState.AVAILABLE);
        StockPosition changed =
                operation.applyTo(initial, StockState.BLOCKED, StockQuantity.of(3));
        assertEquals(StockState.BLOCKED, changed.stockState());
        assertEquals(StockQuantity.of(3), changed.quantity());
        assertEquals(StockState.AVAILABLE, initial.stockState());
        assertEquals(StockQuantity.of(5), initial.quantity());
    }

    @Test
    void applyToRejectsMismatchedPosition() {
        WarehouseOperation operation = sampleOperation();
        StockPosition other =
                StockPosition.of(
                        WarehouseId.generate(),
                        StorageCellId.generate(),
                        MaterialReference.of("OTHER"),
                        StockState.AVAILABLE,
                        StockQuantity.of(1));
        assertThrows(
                InvalidWarehouseStateException.class,
                () -> operation.applyTo(other, StockState.AVAILABLE, StockQuantity.of(2)));
    }

    private static WarehouseOperation sampleOperation() {
        return WarehouseOperation.describe(
                WarehouseOperationId.generate(),
                WarehouseOperationType.RECEIPT,
                MaterialReference.of("VEKA-103.211"),
                WarehouseId.generate(),
                StorageCellId.generate(),
                StockQuantity.of(5));
    }
}

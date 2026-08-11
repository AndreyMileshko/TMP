package com.tmp.warehouse.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WarehouseOperationTest {

    @Test
    void describeCreatesDraftOperationWithoutExecutionSideEffects() {
        WarehouseOperation operation = sampleOperation();
        assertEquals(WarehouseOperationType.RECEIPT, operation.type());
        assertEquals(StockQuantity.of(5), operation.quantity());
        assertEquals(WarehouseOperationStatus.DRAFT, operation.status());
        assertTrue(operation.isExecutable());
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
                        MaterialReference.legacyArticle("OTHER"),
                        StockState.AVAILABLE,
                        StockQuantity.of(1));
        assertThrows(
                InvalidWarehouseStateException.class,
                () -> operation.applyTo(other, StockState.AVAILABLE, StockQuantity.of(2)));
    }

    @Test
    void completeTransitionsDraftToCompleted() {
        WarehouseOperation completed = sampleOperation().complete();
        assertEquals(WarehouseOperationStatus.COMPLETED, completed.status());
        assertFalse(completed.isExecutable());
    }

    @Test
    void failTransitionsDraftToFailed() {
        WarehouseOperation failed = sampleOperation().fail();
        assertEquals(WarehouseOperationStatus.FAILED, failed.status());
        assertFalse(failed.isExecutable());
    }

    @Test
    void completedOperationCannotBeExecutedAgain() {
        WarehouseOperation completed = sampleOperation().complete();
        assertThrows(InvalidWarehouseStateException.class, completed::ensureDraft);
        assertThrows(InvalidWarehouseStateException.class, completed::complete);
        assertThrows(InvalidWarehouseStateException.class, completed::fail);
    }

    private static WarehouseOperation sampleOperation() {
        return WarehouseOperation.describe(
                WarehouseOperationId.generate(),
                WarehouseOperationType.RECEIPT,
                MaterialReference.legacyArticle("VEKA-103.211"),
                WarehouseId.generate(),
                StorageCellId.generate(),
                StockQuantity.of(5));
    }
}

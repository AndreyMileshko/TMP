package com.tmp.warehouse.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TransferOperationContextTest {

    @Test
    void deferredContextOmitsDestinationCell() {
        WarehouseOperationId operationId = WarehouseOperationId.generate();
        WarehouseId destination = WarehouseId.generate();
        TransferOperationContext context =
                TransferOperationContext.deferredDestination(operationId, destination);

        assertEquals(destination, context.destinationWarehouseId());
        assertNull(context.destinationStorageCellId());
        assertTrue(context.destinationStorageCellIdOptional().isEmpty());
        assertFalse(context.hasDestinationStorageCell());
        assertFalse(context.isReceived());
    }

    @Test
    void legacyConstructorRequiresDestinationCell() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new TransferOperationContext(
                                WarehouseOperationId.generate(),
                                WarehouseId.generate(),
                                null));
    }

    @Test
    void withReceiveClaimPersistsActualCell() {
        WarehouseOperationId operationId = WarehouseOperationId.generate();
        WarehouseId destination = WarehouseId.generate();
        StorageCellId cell = StorageCellId.generate();
        WarehouseOperationId receiveId = WarehouseOperationId.generate();

        TransferOperationContext claimed =
                TransferOperationContext.deferredDestination(operationId, destination)
                        .withReceiveClaim(receiveId, cell);

        assertEquals(cell, claimed.destinationStorageCellId());
        assertEquals(receiveId, claimed.receiveOperationId());
        assertTrue(claimed.isReceived());
    }
}

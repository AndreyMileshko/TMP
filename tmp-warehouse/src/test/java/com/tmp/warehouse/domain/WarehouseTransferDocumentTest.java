package com.tmp.warehouse.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WarehouseTransferDocumentTest {

    @Test
    void rejectsSameSourceAndDestination() {
        WarehouseId warehouse = WarehouseId.generate();
        assertThrows(
                InvalidWarehouseStateException.class,
                () ->
                        WarehouseTransferDocument.create(
                                UUID.randomUUID(), warehouse, warehouse, List.of()));
    }

    @Test
    void rejectsZeroAndNegativeQuantity() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        WarehouseTransferLine.of(
                                WarehouseTransferLineId.generate(),
                                MaterialReferenceId.generate(),
                                StockQuantity.of(BigDecimal.ZERO),
                                1));
        assertThrows(
                IllegalArgumentException.class,
                () -> StockQuantity.of(BigDecimal.valueOf(-1)));
    }

    @Test
    void rejectsDuplicateMaterial() {
        MaterialReferenceId material = MaterialReferenceId.generate();
        WarehouseTransferLine line1 =
                WarehouseTransferLine.of(
                        WarehouseTransferLineId.generate(),
                        material,
                        StockQuantity.of(BigDecimal.ONE),
                        1);
        WarehouseTransferLine line2 =
                WarehouseTransferLine.of(
                        WarehouseTransferLineId.generate(),
                        material,
                        StockQuantity.of(BigDecimal.TEN),
                        2);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        WarehouseTransferDocument.create(
                                UUID.randomUUID(),
                                WarehouseId.generate(),
                                WarehouseId.generate(),
                                List.of(line1, line2)));
    }

    @Test
    void orderedLinesSortByLineOrder() {
        WarehouseTransferLine second =
                WarehouseTransferLine.of(
                        WarehouseTransferLineId.generate(),
                        MaterialReferenceId.generate(),
                        StockQuantity.of(BigDecimal.TEN),
                        2);
        WarehouseTransferLine first =
                WarehouseTransferLine.of(
                        WarehouseTransferLineId.generate(),
                        MaterialReferenceId.generate(),
                        StockQuantity.of(BigDecimal.ONE),
                        1);
        WarehouseTransferDocument document =
                WarehouseTransferDocument.create(
                        UUID.randomUUID(),
                        WarehouseId.generate(),
                        WarehouseId.generate(),
                        List.of(second, first));
        assertEquals(1, document.orderedLines().get(0).lineOrder());
        assertEquals(2, document.orderedLines().get(1).lineOrder());
    }

    @Test
    void payloadRevisionStartsAtZeroAndIncrementsOnEdit() {
        WarehouseTransferDocument document =
                WarehouseTransferDocument.create(
                        UUID.randomUUID(),
                        WarehouseId.generate(),
                        WarehouseId.generate(),
                        List.of());
        assertEquals(0L, document.payloadRevision());
        WarehouseTransferDocument updated =
                document.withContent(
                        document.sourceWarehouseId(),
                        document.destinationWarehouseId(),
                        List.of(),
                        0L);
        assertEquals(1L, updated.payloadRevision());
    }

    @Test
    void staleRevisionRejected() {
        WarehouseTransferDocument document =
                WarehouseTransferDocument.create(
                        UUID.randomUUID(),
                        WarehouseId.generate(),
                        WarehouseId.generate(),
                        List.of());
        TransferDocumentOptimisticLockException ex =
                assertThrows(
                        TransferDocumentOptimisticLockException.class,
                        () ->
                                document.withContent(
                                        document.sourceWarehouseId(),
                                        document.destinationWarehouseId(),
                                        List.of(),
                                        5L));
        assertEquals(0L, ex.actualRevision());
        assertEquals(5L, ex.expectedRevision());
        assertTrue(ex.getMessage().contains("revision mismatch"));
    }

    @Test
    void emptyDraftAllowed() {
        WarehouseTransferDocument document =
                WarehouseTransferDocument.create(
                        UUID.randomUUID(),
                        WarehouseId.generate(),
                        WarehouseId.generate(),
                        List.of());
        assertTrue(document.lines().isEmpty());
    }

    @Test
    void withContentPreservesContinuationLineage() {
        UUID parent = UUID.randomUUID();
        WarehouseTransferLine line =
                WarehouseTransferLine.of(
                        WarehouseTransferLineId.generate(),
                        MaterialReferenceId.generate(),
                        StockQuantity.of(BigDecimal.TEN),
                        1);
        WarehouseTransferDocument continuation =
                WarehouseTransferDocument.createContinuation(
                        UUID.randomUUID(),
                        parent,
                        TransferContinuationReason.SHORTFALL,
                        WarehouseId.generate(),
                        WarehouseId.generate(),
                        List.of(line));
        WarehouseTransferDocument updated =
                continuation.withContent(
                        continuation.sourceWarehouseId(),
                        continuation.destinationWarehouseId(),
                        List.of(line),
                        0L);
        assertEquals(1L, updated.payloadRevision());
        assertEquals(parent, updated.continuationOfDocumentId().orElseThrow());
        assertEquals(
                TransferContinuationReason.SHORTFALL,
                updated.continuationReason().orElseThrow());
    }

    @Test
    void rejectsPartialLineagePair() {
        assertThrows(
                InvalidWarehouseStateException.class,
                () ->
                        WarehouseTransferDocument.of(
                                UUID.randomUUID(),
                                WarehouseId.generate(),
                                WarehouseId.generate(),
                                1,
                                0L,
                                List.of(),
                                UUID.randomUUID(),
                                null));
    }
}

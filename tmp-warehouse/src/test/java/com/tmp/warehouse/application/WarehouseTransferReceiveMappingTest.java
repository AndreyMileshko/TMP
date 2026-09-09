package com.tmp.warehouse.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tmp.warehouse.application.WarehouseTransferReceiveService.DestinationAllocationInput;
import com.tmp.warehouse.application.WarehouseTransferReceiveService.ReceiveSegment;
import com.tmp.warehouse.domain.MaterialReferenceId;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.TransferDocumentSendAllocation;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.WarehouseOperationId;
import com.tmp.warehouse.domain.WarehouseTransferDocument;
import com.tmp.warehouse.domain.WarehouseTransferLine;
import com.tmp.warehouse.domain.WarehouseTransferLineId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Unit coverage for deterministic send→receive mapping used by partial receive. */
class WarehouseTransferReceiveMappingTest {

    @Test
    void mapSendToReceivePartialMultiCellSegments() {
        Instant now = Instant.parse("2026-09-09T05:00:00Z");
        WarehouseTransferLineId lineId = WarehouseTransferLineId.generate();
        MaterialReferenceId materialId = MaterialReferenceId.generate();
        UUID documentId = UUID.randomUUID();
        UUID cellA1 = UUID.randomUUID();
        UUID cellA2 = UUID.randomUUID();
        UUID cellB1 = UUID.randomUUID();
        UUID cellB2 = UUID.randomUUID();
        UUID sendAlloc1 = UUID.randomUUID();
        UUID sendAlloc2 = UUID.randomUUID();

        WarehouseTransferDocument payload =
                WarehouseTransferDocument.create(
                        documentId,
                        WarehouseId.generate(),
                        WarehouseId.generate(),
                        List.of(
                                WarehouseTransferLine.of(
                                        lineId,
                                        materialId,
                                        StockQuantity.of(new BigDecimal("100")),
                                        1)));

        List<TransferDocumentSendAllocation> sendAllocations =
                List.of(
                        TransferDocumentSendAllocation.of(
                                sendAlloc1,
                                documentId,
                                lineId,
                                StorageCellId.of(cellA1),
                                StockQuantity.of(new BigDecimal("60")),
                                WarehouseOperationId.generate(),
                                now),
                        TransferDocumentSendAllocation.of(
                                sendAlloc2,
                                documentId,
                                lineId,
                                StorageCellId.of(cellA2),
                                StockQuantity.of(new BigDecimal("40")),
                                WarehouseOperationId.generate(),
                                now.plusSeconds(1)));

        List<DestinationAllocationInput> destinations =
                List.of(
                        new DestinationAllocationInput(lineId.value(), cellB1, new BigDecimal("50")),
                        new DestinationAllocationInput(lineId.value(), cellB2, new BigDecimal("25")));

        List<ReceiveSegment> segments =
                WarehouseTransferReceiveService.mapSendToReceive(
                        payload, sendAllocations, destinations, true);

        Map<UUID, BigDecimal> acceptedBySend =
                segments.stream()
                        .collect(
                                Collectors.groupingBy(
                                        ReceiveSegment::sendAllocationId,
                                        Collectors.mapping(
                                                ReceiveSegment::quantity,
                                                Collectors.reducing(
                                                        BigDecimal.ZERO, BigDecimal::add))));

        assertEquals(0, acceptedBySend.get(sendAlloc1).compareTo(new BigDecimal("60")));
        assertEquals(0, acceptedBySend.get(sendAlloc2).compareTo(new BigDecimal("15")));
        assertEquals(
                0,
                segments.stream()
                        .map(ReceiveSegment::quantity)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .compareTo(new BigDecimal("75")));
    }
}

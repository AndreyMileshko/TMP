package com.tmp.warehouse.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentStatus;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentPrintLineView;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentPrintPlannedCellAllocation;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentPrintView;
import com.tmp.warehouse.api.WarehouseApi.TransferPrintAllocationKind;
import com.tmp.warehouse.api.WarehouseApi.TransferPrintDocumentKind;
import com.tmp.warehouse.application.TransferDocumentPrintAssembler.MaterialDisplay;
import com.tmp.warehouse.domain.MaterialReferenceId;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StorageCell;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.TransferDocumentSendAllocation;
import com.tmp.warehouse.domain.TransferReturnSettlementItem;
import com.tmp.warehouse.domain.Warehouse;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.WarehouseOperationId;
import com.tmp.warehouse.domain.WarehouseTransferDocument;
import com.tmp.warehouse.domain.WarehouseTransferLine;
import com.tmp.warehouse.domain.WarehouseTransferLineId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransferDocumentPrintAssemblerTest {

    private static final UUID DOC_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID LINE_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID MATERIAL_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID SRC_WH = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID DST_WH = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID CELL_A = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final UUID CELL_B = UUID.fromString("77777777-7777-4777-8777-777777777777");
    private static final UUID CELL_DEST = UUID.fromString("88888888-8888-4888-8888-888888888888");
    private static final Instant CREATED = Instant.parse("2026-09-11T10:00:00Z");

    @Test
    void pickingDraftUsesPlannedAllocationsAndMarksPlanned() {
        TransferDocumentPrintView view =
                TransferDocumentPrintAssembler.assemble(
                        metadata(DocumentStatus.DRAFT),
                        payload(line(LINE_ID, "10")),
                        warehouse(SRC_WH, "SRC", "Source"),
                        warehouse(DST_WH, "DST", "Destination"),
                        List.of("26160801"),
                        TransferPrintDocumentKind.TRANSFER_PICKING,
                        List.of(),
                        List.of(),
                        cells(),
                        materials(),
                        List.of(
                                new TransferDocumentPrintPlannedCellAllocation(
                                        LINE_ID, CELL_A, new BigDecimal("4")),
                                new TransferDocumentPrintPlannedCellAllocation(
                                        LINE_ID, CELL_B, new BigDecimal("6"))));

        assertEquals(TransferDocumentPrintAssembler.HEADING_PICKING, view.printHeading());
        assertEquals(TransferPrintDocumentKind.TRANSFER_PICKING, view.documentKind());
        assertEquals(TransferPrintAllocationKind.PLANNED, view.allocationKind());
        assertTrue(view.plannedAllocations());
        assertEquals(TransferDocumentPrintAssembler.NOTE_PLANNED_PICKING, view.advisoryNote());
        assertEquals(2, view.lines().size());
        assertEquals("A-01", view.lines().get(0).storageCellCode());
        assertEquals("B-02", view.lines().get(1).storageCellCode());
        assertEquals(0, new BigDecimal("4").compareTo(view.lines().get(0).quantity()));
        assertEquals(TransferPrintAllocationKind.PLANNED, view.lines().get(0).allocationKind());
        assertEquals(
                List.of("Ячейка", "Артикул", "Наименование", "Цвет", "Размер", "Количество", "Ед."),
                TransferDocumentPrintLineView.COLUMN_HEADERS);
        assertEquals("ART-1", view.lines().get(0).article());
        assertEquals("Ткань", view.lines().get(0).materialName());
        assertEquals("Белый", view.lines().get(0).color());
        assertEquals("6000", view.lines().get(0).size());
        assertEquals("м", view.lines().get(0).unitOfMeasure());
    }

    @Test
    void pickingDraftWithoutCellShowsUnassignedMarker() {
        TransferDocumentPrintView view =
                TransferDocumentPrintAssembler.assemble(
                        metadata(DocumentStatus.DRAFT),
                        payload(line(LINE_ID, "10")),
                        warehouse(SRC_WH, "SRC", "Source"),
                        warehouse(DST_WH, "DST", "Destination"),
                        List.of(),
                        TransferPrintDocumentKind.TRANSFER_PICKING,
                        List.of(),
                        List.of(),
                        cells(),
                        materials(),
                        List.of(
                                new TransferDocumentPrintPlannedCellAllocation(
                                        LINE_ID, null, new BigDecimal("10"))));

        assertEquals(1, view.lines().size());
        assertNull(view.lines().get(0).storageCellId());
        assertEquals("—", view.lines().get(0).storageCellCode());
    }

    @Test
    void pickingPostedUsesPersistedSendAllocationsOnly() {
        UUID allocationId = UUID.fromString("99999999-9999-4999-8999-999999999999");
        TransferDocumentSendAllocation send =
                TransferDocumentSendAllocation.pending(
                        allocationId,
                        DOC_ID,
                        WarehouseTransferLineId.of(LINE_ID),
                        StorageCellId.of(CELL_A),
                        StockQuantity.of(new BigDecimal("10")),
                        CREATED);

        TransferDocumentPrintView view =
                TransferDocumentPrintAssembler.assemble(
                        metadata(DocumentStatus.CLOSED),
                        payload(line(LINE_ID, "10")),
                        warehouse(SRC_WH, "SRC", "Source"),
                        warehouse(DST_WH, "DST", "Destination"),
                        List.of("26160682"),
                        TransferPrintDocumentKind.TRANSFER_PICKING,
                        List.of(send),
                        List.of(),
                        cells(),
                        materials(),
                        List.of(
                                new TransferDocumentPrintPlannedCellAllocation(
                                        LINE_ID, CELL_B, new BigDecimal("99"))));

        assertEquals(TransferDocumentPrintAssembler.HEADING_PICKING, view.printHeading());
        assertEquals(TransferPrintAllocationKind.CONFIRMED, view.allocationKind());
        assertFalse(view.plannedAllocations());
        assertNull(view.advisoryNote());
        assertEquals(1, view.lines().size());
        TransferDocumentPrintLineView row = view.lines().get(0);
        assertEquals("A-01", row.storageCellCode());
        assertEquals(0, new BigDecimal("10").compareTo(row.quantity()));
        assertEquals(TransferPrintAllocationKind.CONFIRMED, row.allocationKind());
    }

    @Test
    void returnPlannedUsesDestinationCellsAndReturnHeading() {
        TransferDocumentPrintView view =
                TransferDocumentPrintAssembler.assemble(
                        metadata(DocumentStatus.POSTED),
                        payload(line(LINE_ID, "10")),
                        warehouse(SRC_WH, "SRC", "Source"),
                        warehouse(DST_WH, "DST", "Destination"),
                        List.of(),
                        TransferPrintDocumentKind.MATERIAL_RETURN,
                        List.of(),
                        List.of(),
                        cells(),
                        materials(),
                        List.of(
                                new TransferDocumentPrintPlannedCellAllocation(
                                        LINE_ID, CELL_A, new BigDecimal("10"))));

        assertEquals(TransferDocumentPrintAssembler.HEADING_RETURN, view.printHeading());
        assertEquals(TransferPrintDocumentKind.MATERIAL_RETURN, view.documentKind());
        assertTrue(view.plannedAllocations());
        assertEquals(TransferDocumentPrintAssembler.NOTE_PLANNED_RETURN, view.advisoryNote());
        assertEquals(SRC_WH, view.destinationWarehouseId());
        assertEquals(DST_WH, view.sourceWarehouseId());
        assertEquals("A-01", view.lines().get(0).storageCellCode());
    }

    @Test
    void returnConfirmedUsesReturnSettlementDestinationCells() {
        UUID allocationId = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        TransferDocumentSendAllocation send =
                TransferDocumentSendAllocation.pending(
                        allocationId,
                        DOC_ID,
                        WarehouseTransferLineId.of(LINE_ID),
                        StorageCellId.of(CELL_A),
                        StockQuantity.of(new BigDecimal("10")),
                        CREATED);
        TransferReturnSettlementItem returned =
                TransferReturnSettlementItem.of(
                        UUID.randomUUID(),
                        DOC_ID,
                        allocationId,
                        StorageCellId.of(CELL_A),
                        StockQuantity.of(new BigDecimal("7")),
                        WarehouseOperationId.of(UUID.randomUUID()),
                        CREATED);

        TransferDocumentPrintView view =
                TransferDocumentPrintAssembler.assemble(
                        metadata(DocumentStatus.CLOSED),
                        payload(line(LINE_ID, "10")),
                        warehouse(SRC_WH, "SRC", "Source"),
                        warehouse(DST_WH, "DST", "Destination"),
                        List.of(),
                        TransferPrintDocumentKind.MATERIAL_RETURN,
                        List.of(send),
                        List.of(returned),
                        cells(),
                        materials(),
                        List.of());

        assertEquals(TransferDocumentPrintAssembler.HEADING_RETURN, view.printHeading());
        assertFalse(view.plannedAllocations());
        assertEquals("Возврат по документу № TR-100", view.advisoryNote());
        assertEquals(1, view.lines().size());
        assertEquals("A-01", view.lines().get(0).storageCellCode());
        assertEquals(0, new BigDecimal("7").compareTo(view.lines().get(0).quantity()));
        assertEquals(TransferPrintAllocationKind.CONFIRMED, view.lines().get(0).allocationKind());
    }

    private static DocumentMetadata metadata(DocumentStatus status) {
        return new DocumentMetadata(
                DOC_ID,
                "warehouse.transfer",
                "TR-100",
                "Перемещение материалов",
                status,
                1L,
                CREATED,
                CREATED,
                status == DocumentStatus.DRAFT ? null : CREATED,
                status == DocumentStatus.CLOSED ? CREATED : null);
    }

    private static WarehouseTransferDocument payload(WarehouseTransferLine... lines) {
        return WarehouseTransferDocument.of(
                DOC_ID,
                WarehouseId.of(SRC_WH),
                WarehouseId.of(DST_WH),
                1,
                0L,
                List.of(lines));
    }

    private static WarehouseTransferLine line(UUID lineId, String qty) {
        return WarehouseTransferLine.of(
                WarehouseTransferLineId.of(lineId),
                MaterialReferenceId.of(MATERIAL_ID),
                StockQuantity.of(new BigDecimal(qty)),
                1);
    }

    private static Warehouse warehouse(UUID id, String code, String name) {
        return Warehouse.of(WarehouseId.of(id), code, name, true);
    }

    private static Map<UUID, StorageCell> cells() {
        Map<UUID, StorageCell> map = new HashMap<>();
        map.put(
                CELL_A,
                StorageCell.of(StorageCellId.of(CELL_A), WarehouseId.of(SRC_WH), "A-01", true));
        map.put(
                CELL_B,
                StorageCell.of(StorageCellId.of(CELL_B), WarehouseId.of(SRC_WH), "B-02", true));
        map.put(
                CELL_DEST,
                StorageCell.of(
                        StorageCellId.of(CELL_DEST), WarehouseId.of(DST_WH), "D-10", true));
        return map;
    }

    private static Map<UUID, MaterialDisplay> materials() {
        return Map.of(
                MATERIAL_ID, new MaterialDisplay("ART-1", "Ткань", "Белый", "6000", "м"));
    }
}
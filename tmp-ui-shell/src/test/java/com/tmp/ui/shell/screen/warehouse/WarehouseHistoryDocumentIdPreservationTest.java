package com.tmp.ui.shell.screen.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.HistoryRow;
import com.tmp.warehouse.api.WarehouseApi;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WarehouseHistoryDocumentIdPreservationTest {

    @Test
    void preservesDocumentIdAndTransferPrintEligibility() {
        UUID documentId = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        HistoryRow row =
                HistoryRow.from(
                        entry(documentId, "TRANSFER_SEND", "TR-42"), false);

        assertEquals(documentId, row.documentId());
        assertEquals("TRANSFER_SEND", row.operationType());
        assertTrue(row.hasTransferDocument());
    }

    @Test
    void nonTransferHistoryIsNotPrintableAsTransferDocument() {
        HistoryRow row =
                HistoryRow.from(entry(UUID.randomUUID(), "CONSUMPTION", "REL-1"), false);
        assertFalse(row.hasTransferDocument());
    }

    @Test
    void returnHistoryRemainsEligibleForOriginalTransferPrint() {
        UUID documentId = UUID.randomUUID();
        HistoryRow row =
                HistoryRow.from(entry(documentId, "TRANSFER_RETURN", "TR-9"), false);
        assertTrue(row.hasTransferDocument());
        assertEquals(documentId, row.documentId());
    }

    private static WarehouseApi.WarehouseHistoryEntryView entry(
            UUID documentId, String operationType, String documentNumber) {
        return new WarehouseApi.WarehouseHistoryEntryView(
                UUID.randomUUID(),
                Instant.parse("2026-09-01T10:15:00Z"),
                operationType,
                operationType,
                UUID.randomUUID(),
                "ART",
                "Material",
                "",
                "",
                "шт",
                BigDecimal.ONE,
                UUID.randomUUID(),
                "SRC",
                UUID.randomUUID(),
                "A-01",
                UUID.randomUUID(),
                "DST",
                UUID.randomUUID(),
                "B-01",
                documentId,
                documentNumber,
                List.of(),
                null,
                null,
                null);
    }
}

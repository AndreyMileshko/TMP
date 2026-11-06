package com.tmp.ui.shell.screen.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tmp.warehouse.api.WarehouseApi;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WarehouseHistoryOriginFormatTest {

    @Test
    void orderColumnShowsSingleOrderNumber() {
        assertEquals(
                "26106082",
                WarehouseWorkspaceViewModel.HistoryRow.formatHistoryOrders(
                        entry(
                                "CONSUMPTION",
                                "PRODUCTION-RELEASE-CCC06B9F",
                                List.of("26106082"))));
    }

    @Test
    void orderColumnShowsMultipleOrderNumbers() {
        assertEquals(
                "26106082, 26106083",
                WarehouseWorkspaceViewModel.HistoryRow.formatHistoryOrders(
                        entry(
                                "CONSUMPTION",
                                "PRODUCTION-RELEASE-AAA11111",
                                List.of("26106082", "26106083"))));
    }

    @Test
    void orderColumnShowsDashWhenMissing() {
        assertEquals(
                "—",
                WarehouseWorkspaceViewModel.HistoryRow.formatHistoryOrders(
                        entry("TRANSFER_SEND", "TR-77", List.of())));
    }

    @Test
    void originKeepsReleaseDocumentWithoutEmbeddingOrders() {
        assertEquals(
                "Выпуск PRODUCTION-RELEASE-CCC06B9F",
                WarehouseWorkspaceViewModel.HistoryRow.formatHistoryOrigin(
                        entry(
                                "CONSUMPTION",
                                "PRODUCTION-RELEASE-CCC06B9F",
                                List.of("26106082"))));
    }

    @Test
    void legacyMissingOriginShowsDash() {
        assertEquals(
                "—",
                WarehouseWorkspaceViewModel.HistoryRow.formatHistoryOrigin(
                        entry("CONSUMPTION", null, List.of())));
    }

    @Test
    void transferDocumentNumberWithoutOrdersShowsDocumentNumber() {
        assertEquals(
                "TR-77",
                WarehouseWorkspaceViewModel.HistoryRow.formatHistoryOrigin(
                        entry("TRANSFER_SEND", "TR-77", List.of())));
    }

    @Test
    void transferDocumentWithRelatedOrdersKeepsDocumentInOrigin() {
        assertEquals(
                "WAREHOUSE-TRANSFER-8E5C5C08",
                WarehouseWorkspaceViewModel.HistoryRow.formatHistoryOrigin(
                        entry(
                                "TRANSFER_SEND",
                                "WAREHOUSE-TRANSFER-8E5C5C08",
                                List.of("26106075"))));
        assertEquals(
                "26106075",
                WarehouseWorkspaceViewModel.HistoryRow.formatHistoryOrders(
                        entry(
                                "TRANSFER_SEND",
                                "WAREHOUSE-TRANSFER-8E5C5C08",
                                List.of("26106075"))));
    }

    private static WarehouseApi.WarehouseHistoryEntryView entry(
            String operationType, String documentNumber, List<String> orderNumbers) {
        return new WarehouseApi.WarehouseHistoryEntryView(
                UUID.randomUUID(),
                Instant.parse("2026-09-01T10:15:00Z"),
                operationType,
                "Списание",
                UUID.randomUUID(),
                "ART",
                "Material",
                "",
                "",
                "шт",
                BigDecimal.ONE,
                UUID.randomUUID(),
                "PROD",
                UUID.randomUUID(),
                "A-01",
                null,
                null,
                null,
                null,
                documentNumber == null ? null : UUID.randomUUID(),
                documentNumber,
                orderNumbers,
                null,
                null,
                null);
    }
}

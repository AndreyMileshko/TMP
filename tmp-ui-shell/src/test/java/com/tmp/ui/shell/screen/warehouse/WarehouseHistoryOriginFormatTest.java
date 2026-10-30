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
    void formatsSingleOrderProductionReleaseOrigin() {
        assertEquals(
                "Заказ 26106082 · Выпуск PRODUCTION-RELEASE-CCC06B9F",
                WarehouseWorkspaceViewModel.HistoryRow.formatHistoryOrigin(
                        entry(
                                "PRODUCTION-RELEASE-CCC06B9F",
                                List.of("26106082"))));
    }

    @Test
    void formatsMultiOrderProductionReleaseOrigin() {
        assertEquals(
                "Заказы 26106082, 26106083 · Выпуск PRODUCTION-RELEASE-AAA11111",
                WarehouseWorkspaceViewModel.HistoryRow.formatHistoryOrigin(
                        entry(
                                "PRODUCTION-RELEASE-AAA11111",
                                List.of("26106082", "26106083"))));
    }

    @Test
    void legacyMissingOriginShowsDash() {
        assertEquals(
                "—",
                WarehouseWorkspaceViewModel.HistoryRow.formatHistoryOrigin(entry(null, List.of())));
    }

    @Test
    void transferDocumentNumberWithoutOrdersShowsDocumentNumber() {
        assertEquals(
                "TR-77",
                WarehouseWorkspaceViewModel.HistoryRow.formatHistoryOrigin(
                        entry("TR-77", List.of())));
    }

    private static WarehouseApi.WarehouseHistoryEntryView entry(
            String documentNumber, List<String> orderNumbers) {
        return new WarehouseApi.WarehouseHistoryEntryView(
                UUID.randomUUID(),
                Instant.parse("2026-09-01T10:15:00Z"),
                "CONSUMPTION",
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

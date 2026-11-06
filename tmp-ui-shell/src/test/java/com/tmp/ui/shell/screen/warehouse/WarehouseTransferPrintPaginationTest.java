package com.tmp.ui.shell.screen.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.warehouse.api.WarehouseApi.TransferDocumentPrintLineView;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentPrintView;
import com.tmp.warehouse.api.WarehouseApi.TransferPrintAllocationKind;
import com.tmp.warehouse.api.WarehouseApi.TransferPrintDocumentKind;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WarehouseTransferPrintPaginationTest {

    @Test
    void splitsLinesAcrossPagesAndKeepsOrder() {
        List<TransferDocumentPrintLineView> lines = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            lines.add(line(i, "Material " + i));
        }
        TransferDocumentPrintView document = document(lines);
        List<List<TransferDocumentPrintLineView>> pages =
                WarehouseTransferPrintPagination.paginate(document, 10);

        assertEquals(3, pages.size());
        assertEquals(10, pages.get(0).size());
        assertEquals(10, pages.get(1).size());
        assertEquals(5, pages.get(2).size());
        assertEquals(1, pages.get(0).get(0).rowNumber());
        assertEquals(25, pages.get(2).get(4).rowNumber());
    }

    @Test
    void emptyDocumentYieldsSingleEmptyPage() {
        List<List<TransferDocumentPrintLineView>> pages =
                WarehouseTransferPrintPagination.paginate(
                        document(List.of()),
                        WarehouseTransferPrintPagination.DEFAULT_ROW_UNITS_PER_PAGE);
        assertEquals(1, pages.size());
        assertTrue(pages.get(0).isEmpty());
    }

    @Test
    void landscapeDefaultsUseLowerHeightBudgetAndWiderNameWrap() {
        assertEquals(12, WarehouseTransferPrintPagination.DEFAULT_ROW_UNITS_PER_PAGE);
        assertEquals(44, WarehouseTransferPrintPagination.NAME_CHARS_PER_LINE);
        assertTrue(
                WarehouseTransferPrintPagination.DEFAULT_ROW_UNITS_PER_PAGE < 18,
                "landscape height budget must be below former portrait budget");
    }

    @Test
    void defaultPaginateUsesLandscapeRowBudget() {
        List<TransferDocumentPrintLineView> lines = new ArrayList<>();
        for (int i = 1; i <= 13; i++) {
            lines.add(line(i, "Material " + i));
        }
        List<List<TransferDocumentPrintLineView>> pages =
                WarehouseTransferPrintPagination.paginate(document(lines));
        assertEquals(2, pages.size());
        assertEquals(12, pages.get(0).size());
        assertEquals(1, pages.get(1).size());
    }

    @Test
    void longMaterialNameConsumesExtraRowUnits() {
        TransferDocumentPrintLineView longName =
                line(
                        1,
                        "Очень длинное наименование материала для проверки переноса "
                                + "на несколько строк печатной таблицы склада");
        assertTrue(WarehouseTransferPrintPagination.estimateRowUnits(longName) > 1);
        List<List<TransferDocumentPrintLineView>> pages =
                WarehouseTransferPrintPagination.paginate(
                        document(List.of(longName, line(2, "Short"), line(3, "Short"))), 3);
        assertEquals(2, pages.size());
        assertEquals(1, pages.get(0).size());
    }

    private static TransferDocumentPrintView document(List<TransferDocumentPrintLineView> lines) {
        UUID wh = UUID.randomUUID();
        return new TransferDocumentPrintView(
                UUID.randomUUID(),
                "TR-1",
                Instant.parse("2026-09-11T10:00:00Z"),
                "DRAFT",
                "Перемещение материалов",
                TransferPrintDocumentKind.TRANSFER_PICKING,
                "ЗАДАНИЕ НА КОМПЛЕКТАЦИЮ МАТЕРИАЛОВ",
                List.of(),
                wh,
                "SRC",
                "Source",
                UUID.randomUUID(),
                "DST",
                "Destination",
                TransferPrintAllocationKind.PLANNED,
                true,
                null,
                lines);
    }

    private static TransferDocumentPrintLineView line(int rowNumber, String name) {
        return new TransferDocumentPrintLineView(
                rowNumber,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ART",
                name,
                "",
                "",
                "шт",
                BigDecimal.ONE,
                null,
                "—",
                TransferPrintAllocationKind.PLANNED);
    }
}
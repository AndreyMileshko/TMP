package com.tmp.ui.shell.screen.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.StockRow;
import com.tmp.warehouse.api.WarehouseApi.WarehouseStockCellLineView;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WarehouseMoveDialogSupportTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void createMoveDialogExposesColumnHeadersSelectedCountAndButtonMarkers() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        StockRow row =
                StockRow.from(
                        new WarehouseStockCellLineView(
                                warehouseId,
                                "WH",
                                "Main",
                                UUID.randomUUID(),
                                "A-01",
                                UUID.randomUUID(),
                                "A1",
                                "Pipe",
                                "red",
                                "6м",
                                "м",
                                new BigDecimal("12")));

        AtomicReference<WarehouseMoveDialogSupport.MoveDialogSession> sessionRef =
                new AtomicReference<>();
        JavaFxTestSupport.runOnFxThread(
                () ->
                        sessionRef.set(
                                WarehouseMoveDialogSupport.createMoveDialog(
                                        List.of(row),
                                        warehouseId,
                                        "WH — Main",
                                        List.of(new WarehouseChoice(warehouseId, "WH — Main", true)),
                                        id ->
                                                List.of(
                                                        new StorageCellChoice(
                                                                UUID.randomUUID(),
                                                                warehouseId,
                                                                "B-01",
                                                                true)))));

        WarehouseMoveDialogSupport.MoveDialogSession session = sessionRef.get();
        assertEquals(WarehouseMoveDialogSupport.COLUMN_HEADERS, session.columnHeaders());
        assertEquals("Выбрано позиций: 1", session.headerText());
        assertEquals(
                WarehouseMoveDialogSupport.selectedCountHeader(1), session.headerText());
        List<String> buttons = session.buttonLabels();
        assertTrue(buttons.contains(WarehouseMoveDialogSupport.CANCEL_BUTTON));
        assertTrue(buttons.contains(WarehouseMoveDialogSupport.FILL_AVAILABLE_BUTTON));
        assertTrue(buttons.contains(WarehouseMoveDialogSupport.SUBMIT_SAME_WAREHOUSE_BUTTON));
        assertEquals(WarehouseMoveDialogSupport.TITLE, session.dialog().getTitle());
    }

    @Test
    void createMoveDialogUsesSameHeaderTemplateForMultipleRows() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        StockRow first =
                StockRow.from(
                        new WarehouseStockCellLineView(
                                warehouseId,
                                "WH",
                                "Main",
                                UUID.randomUUID(),
                                "A-01",
                                UUID.randomUUID(),
                                "A1",
                                "Pipe",
                                "",
                                "",
                                "м",
                                new BigDecimal("2")));
        StockRow second =
                StockRow.from(
                        new WarehouseStockCellLineView(
                                warehouseId,
                                "WH",
                                "Main",
                                UUID.randomUUID(),
                                "A-02",
                                UUID.randomUUID(),
                                "A2",
                                "Bolt",
                                "",
                                "",
                                "шт",
                                new BigDecimal("3")));

        AtomicReference<String> header = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThread(
                () ->
                        header.set(
                                WarehouseMoveDialogSupport.createMoveDialog(
                                                List.of(first, second),
                                                warehouseId,
                                                "WH — Main",
                                                List.of(),
                                                id -> List.of())
                                        .headerText()));

        assertEquals("Выбрано позиций: 2", header.get());
        assertEquals(
                "2 м.; 3 шт.",
                WarehouseMoveDialogSupport.formatQuantityTotalsByUnit(List.of(first, second)));
    }

    @Test
    void helpersRemainAvailableForTotalsDisplayAndQuantityValidation() {
        assertEquals("—", WarehouseMoveDialogSupport.displayOrDash(""));
        assertEquals(
                WarehouseMoveDialogSupport.IMPLEMENTATION_ID,
                "WarehouseMoveDialogSupport-table-v1");
        UUID cell = UUID.randomUUID();
        WarehouseMoveDialogSupport.validateNotSelfMove(cell, UUID.randomUUID());
        assertEquals(
                0,
                new BigDecimal("0.5")
                        .compareTo(
                                WarehouseMoveDialogSupport.parseMoveQuantity(
                                        "0,5", new BigDecimal("10"))));
    }
}

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
import javafx.scene.control.TableView;
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

    @Test
    void createMoveDialogUsesResponsiveGeometryAndSaneColumnMins() throws Exception {
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
                                "101.208",
                                "Рама VEKA длинное наименование",
                                "Б/701605",
                                "6500",
                                "м",
                                new BigDecimal("37")));

        AtomicReference<WarehouseMoveDialogSupport.MoveDialogSession> sessionRef =
                new AtomicReference<>();
        JavaFxTestSupport.runOnFxThread(
                () ->
                        sessionRef.set(
                                WarehouseMoveDialogSupport.createMoveDialog(
                                        List.of(row),
                                        warehouseId,
                                        "MAIN — Основной склад",
                                        List.of(
                                                new WarehouseChoice(
                                                        warehouseId, "MAIN — Основной склад", true)),
                                        id -> List.of())));

        WarehouseMoveDialogSupport.MoveDialogSession session = sessionRef.get();
        var pane = session.dialog().getDialogPane();
        assertEquals(WarehouseMoveDialogSupport.DIALOG_PREF_WIDTH, pane.getPrefWidth(), 0.1);
        assertEquals(WarehouseMoveDialogSupport.DIALOG_MIN_WIDTH, pane.getMinWidth(), 0.1);
        assertEquals(WarehouseMoveDialogSupport.DIALOG_PREF_HEIGHT, pane.getPrefHeight(), 0.1);
        assertEquals(WarehouseMoveDialogSupport.DIALOG_MIN_HEIGHT, pane.getMinHeight(), 0.1);
        assertTrue(session.dialog().isResizable());

        TableView<?> table = session.tableView();
        assertEquals(Double.MAX_VALUE, table.getMaxWidth(), 0.1);
        assertEquals(Double.MAX_VALUE, table.getMaxHeight(), 0.1);
        assertTrue(table.getPrefWidth() < 1 || table.getMaxWidth() == Double.MAX_VALUE);
        assertEquals(8, table.getColumns().size());

        assertTrue(session.columnMinWidth("Артикул") >= 110);
        assertTrue(session.columnMinWidth("Наименование") >= 180);
        assertTrue(session.columnMinWidth("Цвет") >= 110);
        assertTrue(session.columnMinWidth("Размер") >= 75);
        assertTrue(session.columnMinWidth("Откуда") >= 90);
        assertTrue(session.columnMinWidth("Доступно") >= 90);
        assertTrue(session.columnMinWidth("Переместить") >= 120);
        assertTrue(session.columnMinWidth("Ед.") >= 55);
        assertTrue(
                session.columnPrefWidth("Наименование")
                        > session.columnPrefWidth("Размер"));
        assertTrue(
                session.columnPrefWidth("Наименование")
                        > session.columnPrefWidth("Ед."));

        AtomicReference<Double> widthAtNarrow = new AtomicReference<>();
        AtomicReference<Double> widthAtWide = new AtomicReference<>();
        AtomicReference<Double> nameAtNarrow = new AtomicReference<>();
        AtomicReference<Double> nameAtWide = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThread(
                () -> {
                    table.resize(820, 320);
                    table.layout();
                    widthAtNarrow.set(table.getWidth());
                    nameAtNarrow.set(session.columnPrefWidth("Наименование"));
                    table.resize(1140, 320);
                    table.layout();
                    widthAtWide.set(table.getWidth());
                    nameAtWide.set(session.columnPrefWidth("Наименование"));
                });

        assertTrue(
                widthAtWide.get() > widthAtNarrow.get(),
                "table width should grow with parent ("
                        + widthAtNarrow.get()
                        + " -> "
                        + widthAtWide.get()
                        + ")");
        assertTrue(
                nameAtWide.get() > nameAtNarrow.get(),
                "name column should receive free width on resize ("
                        + nameAtNarrow.get()
                        + " -> "
                        + nameAtWide.get()
                        + ")");
    }
}

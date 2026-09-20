package com.tmp.ui.shell.screen.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.screen.warehouse.WarehouseReceiptDialogSupport.ReceiptDialogRow;
import com.tmp.ui.shell.screen.warehouse.WarehouseReceiptDialogSupport.ReceiptDialogSession;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WarehouseReceiptDialogSupportTest {

    private static final UUID MAIN_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID SECOND_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID CELL_A01 = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID CELL_B01 = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID CELL_A02 = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void rowRemainsEditableAfterWarehouseSelection() throws Exception {
        AtomicReference<ReceiptDialogSession> sessionRef = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThread(
                () -> {
                    ReceiptDialogSession session = createSession();
                    forceLayout(session);
                    ReceiptDialogRow row = session.rows().get(0);
                    row.materialTextProperty().set("101.208");
                    row.colorTextProperty().set("Белый");
                    row.sizeTextProperty().set("6500");
                    row.quantityTextProperty().set("10");
                    row.setUnitOfMeasure("шт.");

                    WarehouseChoice main = mainWarehouse();
                    selectWarehouseInUi(session.table(), 0, main);
                    forceLayout(session);

                    assertEquals(main, row.warehouse());
                    assertNull(row.cell());

                    TextField material = requireTextField(session.table(), 0, 0);
                    TextField color = requireTextField(session.table(), 0, 1);
                    TextField size = requireTextField(session.table(), 0, 2);
                    TextField qty = requireTextField(session.table(), 0, 3);
                    assertFalse(material.isDisabled());
                    assertFalse(color.isDisabled());
                    assertFalse(size.isDisabled());
                    assertFalse(qty.isDisabled());
                    assertTrue(material.isEditable());
                    assertTrue(color.isEditable());
                    assertTrue(size.isEditable());
                    assertTrue(qty.isEditable());

                    material.setText("101.209");
                    color.setText("Чёрный");
                    size.setText("3000");
                    qty.setText("5");
                    assertEquals("101.209", row.materialTextProperty().get());
                    assertEquals("Чёрный", row.colorTextProperty().get());
                    assertEquals("3000", row.sizeTextProperty().get());
                    assertEquals("5", row.quantityTextProperty().get());

                    ComboBox<WarehouseChoice> warehouseCombo = requireWarehouseCombo(session.table(), 0);
                    assertFalse(warehouseCombo.isDisabled());
                    ComboBox<StorageCellChoice> cellCombo = requireCellCombo(session.table(), 0);
                    assertFalse(cellCombo.isDisabled());

                    sessionRef.set(session);
                });
        assertNotNull(sessionRef.get());
    }

    @Test
    void cellListLoadsWhenWarehouseSelected() throws Exception {
        AtomicInteger loaderCalls = new AtomicInteger();
        JavaFxTestSupport.runOnFxThread(
                () -> {
                    ReceiptDialogSession session =
                            WarehouseReceiptDialogSupport.createReceiptDialog(
                                    List.of(mainWarehouse(), secondWarehouse()),
                                    List.of("шт.", "м"),
                                    id -> {
                                        loaderCalls.incrementAndGet();
                                        return cellsFor(id);
                                    });
                    forceLayout(session);
                    selectWarehouseInUi(session.table(), 0, mainWarehouse());
                    forceLayout(session);

                    ComboBox<StorageCellChoice> cellCombo = requireCellCombo(session.table(), 0);
                    assertFalse(cellCombo.isDisabled());
                    assertEquals(2, cellCombo.getItems().size());
                    assertTrue(
                            cellCombo.getItems().stream()
                                    .anyMatch(c -> "A-01".equals(c.label())));
                    assertTrue(loaderCalls.get() > 0);
                });
    }

    @Test
    void changingWarehouseResetsInvalidCell() throws Exception {
        JavaFxTestSupport.runOnFxThread(
                () -> {
                    ReceiptDialogSession session = createSession();
                    forceLayout(session);
                    ReceiptDialogRow row = session.rows().get(0);

                    row.applyWarehouseSelection(mainWarehouse());
                    forceLayout(session);
                    StorageCellChoice a01 = cellsFor(MAIN_ID).get(0);
                    row.setCell(a01);
                    forceLayout(session);
                    assertEquals(a01, row.cell());

                    row.applyWarehouseSelection(secondWarehouse());
                    forceLayout(session);
                    assertNull(row.cell());
                    ComboBox<StorageCellChoice> cellCombo = requireCellCombo(session.table(), 0);
                    assertFalse(cellCombo.isDisabled());
                    assertTrue(
                            cellCombo.getItems().stream()
                                    .anyMatch(c -> "B-01".equals(c.label())));
                    assertFalse(
                            cellCombo.getItems().stream()
                                    .anyMatch(c -> "A-01".equals(c.label())));
                });
    }

    @Test
    void multipleRowsKeepIndependentWarehouseAndCell() throws Exception {
        JavaFxTestSupport.runOnFxThread(
                () -> {
                    ReceiptDialogSession session = createSession();
                    session.rows().add(new ReceiptDialogRow());
                    forceLayout(session);

                    ReceiptDialogRow first = session.rows().get(0);
                    ReceiptDialogRow second = session.rows().get(1);
                    first.applyWarehouseSelection(mainWarehouse());
                    second.applyWarehouseSelection(secondWarehouse());
                    forceLayout(session);

                    StorageCellChoice a01 = cellsFor(MAIN_ID).get(0);
                    StorageCellChoice b01 = cellsFor(SECOND_ID).get(0);
                    first.setCell(a01);
                    second.setCell(b01);
                    forceLayout(session);

                    assertEquals(mainWarehouse(), first.warehouse());
                    assertEquals(a01, first.cell());
                    assertEquals(secondWarehouse(), second.warehouse());
                    assertEquals(b01, second.cell());

                    first.applyWarehouseSelection(secondWarehouse());
                    forceLayout(session);
                    assertNull(first.cell());
                    assertEquals(b01, second.cell());
                    assertEquals(secondWarehouse(), second.warehouse());

                    ComboBox<StorageCellChoice> firstCells = requireCellCombo(session.table(), 0);
                    ComboBox<StorageCellChoice> secondCells = requireCellCombo(session.table(), 1);
                    assertTrue(
                            firstCells.getItems().stream()
                                    .anyMatch(c -> "B-01".equals(c.label())));
                    assertTrue(
                            secondCells.getItems().stream()
                                    .anyMatch(c -> "B-01".equals(c.label())));
                });
    }

    @Test
    void validationBlocksEmptyCell() throws Exception {
        AtomicReference<ReceiptDialogSession> sessionRef = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThread(
                () -> {
                    ReceiptDialogSession session = createSession();
                    ReceiptDialogRow row = session.rows().get(0);
                    row.materialTextProperty().set("101.208");
                    row.quantityTextProperty().set("10");
                    row.setUnitOfMeasure("шт.");
                    row.applyWarehouseSelection(mainWarehouse());
                    sessionRef.set(session);
                });

        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> sessionRef.get().requireSubmission());
        assertEquals("Укажите ячейку.", ex.getMessage());
    }

    private static ReceiptDialogSession createSession() {
        return WarehouseReceiptDialogSupport.createReceiptDialog(
                List.of(mainWarehouse(), secondWarehouse()),
                List.of("шт.", "м"),
                WarehouseReceiptDialogSupportTest::cellsFor);
    }

    private static WarehouseChoice mainWarehouse() {
        return new WarehouseChoice(MAIN_ID, "MAIN — Основной склад", true);
    }

    private static WarehouseChoice secondWarehouse() {
        return new WarehouseChoice(SECOND_ID, "SECOND — Второй склад", true);
    }

    private static List<StorageCellChoice> cellsFor(UUID warehouseId) {
        if (MAIN_ID.equals(warehouseId)) {
            return List.of(
                    new StorageCellChoice(CELL_A01, MAIN_ID, "A-01", true),
                    new StorageCellChoice(CELL_A02, MAIN_ID, "A-02", true));
        }
        if (SECOND_ID.equals(warehouseId)) {
            return List.of(new StorageCellChoice(CELL_B01, SECOND_ID, "B-01", true));
        }
        return List.of();
    }

    private static void forceLayout(ReceiptDialogSession session) {
        Region pane = session.dialog().getDialogPane();
        pane.applyCss();
        pane.layout();
        TableView<ReceiptDialogRow> table = session.table();
        table.applyCss();
        table.layout();
        table.setPrefWidth(980);
        table.setPrefHeight(260);
        table.resize(980, 260);
        table.layout();
        for (Node node : table.lookupAll(".table-row-cell")) {
            node.applyCss();
            if (node instanceof Region region) {
                region.layout();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void selectWarehouseInUi(
            TableView<ReceiptDialogRow> table, int rowIndex, WarehouseChoice warehouse) {
        ComboBox<WarehouseChoice> combo = requireWarehouseCombo(table, rowIndex);
        combo.getSelectionModel().select(warehouse);
    }

    private static TextField requireTextField(
            TableView<ReceiptDialogRow> table, int rowIndex, int columnIndex) {
        TableCell<?, ?> cell = requireCell(table, rowIndex, columnIndex);
        Node graphic = cell.getGraphic();
        assertNotNull(graphic, "expected TextField graphic at row=" + rowIndex + " col=" + columnIndex);
        assertTrue(graphic instanceof TextField, "graphic must be TextField");
        return (TextField) graphic;
    }

    @SuppressWarnings("unchecked")
    private static ComboBox<WarehouseChoice> requireWarehouseCombo(
            TableView<ReceiptDialogRow> table, int rowIndex) {
        TableCell<?, ?> cell = requireCell(table, rowIndex, 5);
        Node graphic = cell.getGraphic();
        assertNotNull(graphic, "warehouse ComboBox graphic missing");
        assertTrue(graphic instanceof ComboBox<?>);
        return (ComboBox<WarehouseChoice>) graphic;
    }

    @SuppressWarnings("unchecked")
    private static ComboBox<StorageCellChoice> requireCellCombo(
            TableView<ReceiptDialogRow> table, int rowIndex) {
        TableCell<?, ?> cell = requireCell(table, rowIndex, 6);
        Node graphic = cell.getGraphic();
        assertNotNull(graphic, "cell ComboBox graphic missing");
        assertTrue(graphic instanceof ComboBox<?>);
        return (ComboBox<StorageCellChoice>) graphic;
    }

    private static TableCell<?, ?> requireCell(
            TableView<ReceiptDialogRow> table, int rowIndex, int columnIndex) {
        TableColumn<ReceiptDialogRow, ?> column = table.getColumns().get(columnIndex);
        for (Node node : table.lookupAll(".table-row-cell")) {
            if (!(node instanceof TableRow<?> tableRow)) {
                continue;
            }
            if (tableRow.getIndex() != rowIndex) {
                continue;
            }
            for (Node child : tableRow.lookupAll(".table-cell")) {
                if (child instanceof TableCell<?, ?> tableCell
                        && tableCell.getTableColumn() == column
                        && tableCell.getGraphic() != null) {
                    return tableCell;
                }
            }
        }
        throw new AssertionError(
                "TableCell not found for row=" + rowIndex + " column=" + columnIndex);
    }
}

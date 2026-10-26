package com.tmp.ui.shell.screen.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.order.DecimalUiFormat;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.ActionEditRow;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.ReceiveAllocationEditRow;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.SupplyWaitingLineRow;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.TaskRow;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskKind;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskSource;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskState;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskView;
import com.tmp.warehouse.api.WarehouseDemandQueryApi.WarehouseDemandSupplyWaitingLineView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WarehouseTaskDialogSupportTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void dialogTitlesAndReferenceHeadersMatchTaskKind() {
        assertEquals(
                "Подготовка материалов",
                WarehouseTaskDialogSupport.dialogTitleFor(WarehouseTaskKind.TRANSFER_PREPARATION));
        assertEquals(
                "Приёмка материалов",
                WarehouseTaskDialogSupport.dialogTitleFor(WarehouseTaskKind.TRANSFER_RECEIPT));
        assertEquals(
                "Возврат материалов",
                WarehouseTaskDialogSupport.dialogTitleFor(WarehouseTaskKind.RETURN_MATERIALS));
        assertEquals(
                "Подать материалы в производство",
                WarehouseTaskDialogSupport.dialogTitleFor(
                        WarehouseTaskKind.PRODUCTION_MATERIAL_SUPPLY));
        assertEquals(
                "Требуется",
                WarehouseTaskDialogSupport.referenceQuantityHeader(
                        WarehouseTaskKind.TRANSFER_PREPARATION));
        assertEquals(
                "Требуется",
                WarehouseTaskDialogSupport.referenceQuantityHeader(
                        WarehouseTaskKind.PRODUCTION_MATERIAL_SUPPLY));
        assertEquals(
                "Отправлено",
                WarehouseTaskDialogSupport.referenceQuantityHeader(
                        WarehouseTaskKind.TRANSFER_RECEIPT));
        assertEquals(
                "К возврату",
                WarehouseTaskDialogSupport.referenceQuantityHeader(
                        WarehouseTaskKind.RETURN_MATERIALS));
    }

    @Test
    void materialColumnHeadersAreSplitAndQuantityNotRenamed() {
        assertEquals(
                java.util.List.of(
                        "Артикул",
                        "Наименование",
                        "Цвет",
                        "Размер",
                        "Требуется",
                        "Ед.",
                        "Ячейка",
                        "Количество"),
                WarehouseTaskDialogSupport.COLUMN_HEADERS);
        assertFalse(WarehouseTaskDialogSupport.COLUMN_HEADERS.contains("Материал"));
        assertEquals("Количество", WarehouseTaskDialogSupport.COLUMN_HEADERS.get(7));
        assertFalse(WarehouseTaskDialogSupport.COLUMN_HEADERS.contains("Принять"));
        assertFalse(WarehouseTaskDialogSupport.COLUMN_HEADERS.contains("Передать"));
    }

    @Test
    void supplyNameDisplayOmitsGeneratedLengthAnnotation() {
        assertEquals(
                "Рама верхняя",
                WarehouseTaskDialogSupport.supplyNameDisplay("Рама верхняя"));
        assertEquals(
                "Створка боковая",
                WarehouseTaskDialogSupport.supplyNameDisplay("Створка боковая"));
        assertEquals(
                "Порог 14 мм",
                WarehouseTaskDialogSupport.supplyNameDisplay("Порог 14 мм"));
        assertEquals(
                "Рама 40 мм",
                WarehouseTaskDialogSupport.supplyNameDisplay("Рама 40 мм"));
        assertEquals(
                "Импост усиленный 40 мм",
                WarehouseTaskDialogSupport.supplyNameDisplay("Импост усиленный 40 мм"));
        assertEquals("—", WarehouseTaskDialogSupport.supplyNameDisplay(""));
        assertEquals("—", WarehouseTaskDialogSupport.supplyNameDisplay(null));
        assertFalse(
                WarehouseTaskDialogSupport.supplyNameDisplay("Рама верхняя")
                        .contains("Длина"));
    }

    @Test
    void quantityInputFilterAllowsOrderedDigitsAndDecimalSeparator() {
        assertTrue(WarehouseTaskDialogSupport.isAllowedQuantityInput(""));
        assertTrue(WarehouseTaskDialogSupport.isAllowedQuantityInput("1"));
        assertTrue(WarehouseTaskDialogSupport.isAllowedQuantityInput("10"));
        assertTrue(WarehouseTaskDialogSupport.isAllowedQuantityInput("123"));
        assertTrue(WarehouseTaskDialogSupport.isAllowedQuantityInput("10,5"));
        assertTrue(WarehouseTaskDialogSupport.isAllowedQuantityInput("10.5"));
        assertTrue(WarehouseTaskDialogSupport.isAllowedQuantityInput("10,"));
        assertFalse(WarehouseTaskDialogSupport.isAllowedQuantityInput("01a"));
        assertFalse(WarehouseTaskDialogSupport.isAllowedQuantityInput("-1"));
        assertFalse(WarehouseTaskDialogSupport.isAllowedQuantityInput("1,0,5"));
        assertFalse(WarehouseTaskDialogSupport.isAllowedQuantityInput("1.0.5"));
        assertFalse(WarehouseTaskDialogSupport.isAllowedQuantityInput("abc"));

        TextFormatter<String> formatter = WarehouseTaskDialogSupport.quantityTextFormatter();
        TextField field = new TextField();
        field.setTextFormatter(formatter);
        field.setText("");
        field.appendText("1");
        field.appendText("0");
        assertEquals("10", field.getText());
        field.appendText(",");
        field.appendText("5");
        assertEquals("10,5", field.getText());
    }

    @Test
    void createDialogBindsContextualButtonsWithoutAddCell() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(
                () -> {
                    try {
                        TaskRow prep =
                                new TaskRow(
                                        taskView(
                                                WarehouseTaskKind.TRANSFER_PREPARATION,
                                                WarehouseTaskState.NEW),
                                        null);
                        SimpleBooleanProperty canTake = new SimpleBooleanProperty(true);
                        SimpleBooleanProperty editorsEnabled = new SimpleBooleanProperty(false);
                        SimpleBooleanProperty canSend = new SimpleBooleanProperty(false);
                        SimpleBooleanProperty canReceive = new SimpleBooleanProperty(false);
                        SimpleBooleanProperty canReject = new SimpleBooleanProperty(false);
                        SimpleBooleanProperty canReturn = new SimpleBooleanProperty(false);
                        SimpleBooleanProperty canPrepare = new SimpleBooleanProperty(false);
                        SimpleBooleanProperty loading = new SimpleBooleanProperty(false);

                        WarehouseTaskDialogSupport.TaskDialogSession session =
                                WarehouseTaskDialogSupport.createTaskDialog(
                                        prep,
                                        FXCollections.observableArrayList(),
                                        FXCollections.observableArrayList(),
                                        canTake,
                                        editorsEnabled,
                                        canSend,
                                        canReceive,
                                        canReject,
                                        canReturn,
                                        canPrepare,
                                        loading,
                                        () -> {},
                                        () -> {},
                                        () -> {},
                                        reason -> {},
                                        () -> {},
                                        () -> {});

                        assertEquals(
                                WarehouseTaskDialogSupport.TITLE, session.dialog().getTitle());
                        assertEquals("Подготовка материалов", session.titleLabel().getText());
                        assertTrue(session.routeLabel().getText().contains("→"));
                        assertEquals("Документ: TR-1", session.documentLabel().getText());
                        assertTrue(session.documentLabel().isVisible());
                        assertEquals("Требуется", session.referenceQuantityColumn().getText());
                        TableView<?> materials = session.table();
                        assertTrue(materials.getColumns().size() >= 3);
                        TableColumn<?, ?> article = materials.getColumns().get(0);
                        TableColumn<?, ?> color = materials.getColumns().get(2);
                        assertEquals("Артикул", article.getText());
                        assertEquals("Цвет", color.getText());
                        assertTrue(article.getPrefWidth() >= 120.0, "article prefWidth");
                        assertTrue(color.getPrefWidth() >= 110.0, "color prefWidth");
                        assertTrue(article.getMinWidth() >= 90.0);
                        assertTrue(color.getMinWidth() >= 80.0);
                        assertEquals(1020.0, session.dialog().getDialogPane().getPrefWidth(), 0.1);
                        assertEquals(520.0, session.dialog().getDialogPane().getPrefHeight(), 0.1);
                        assertFalse(
                                session.dialog()
                                        .getDialogPane()
                                        .getContent()
                                        .toString()
                                        .contains("Добавить ячейку"));
                        session.close();
                    } catch (Throwable throwable) {
                        error.set(throwable);
                    } finally {
                        latch.countDown();
                    }
                });
        assertTrue(latch.await(15, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError("dialog create failed", error.get());
        }
    }

    @Test
    void supplyDialogNameColumnDoesNotAppendLengthMmAnnotation() throws Exception {
        runOnFx(
                () -> {
                    SupplyWaitingLineRow withLength =
                            new SupplyWaitingLineRow(
                                    new WarehouseDemandSupplyWaitingLineView(
                                            UUID.randomUUID(),
                                            "ART-1",
                                            "Рама верхняя",
                                            "Белый",
                                            "шт",
                                            new BigDecimal("868"),
                                            new BigDecimal("10"),
                                            new BigDecimal("0"),
                                            new BigDecimal("10"),
                                            "NO_AVAILABLE_STOCK"));
                    SupplyWaitingLineRow nameWithSize =
                            new SupplyWaitingLineRow(
                                    new WarehouseDemandSupplyWaitingLineView(
                                            UUID.randomUUID(),
                                            "ART-2",
                                            "Порог 14 мм",
                                            "Белый",
                                            "шт",
                                            new BigDecimal("812"),
                                            new BigDecimal("5"),
                                            new BigDecimal("0"),
                                            new BigDecimal("5"),
                                            "NO_AVAILABLE_STOCK"));
                    ObservableList<ActionEditRow> lines =
                            FXCollections.observableArrayList(withLength, nameWithSize);
                    WarehouseTaskDialogSupport.TaskDialogSession session =
                            WarehouseTaskDialogSupport.createTaskDialog(
                                    new TaskRow(
                                            supplyTaskView(WarehouseTaskState.NEW), null),
                                    lines,
                                    FXCollections.observableArrayList(),
                                    new SimpleBooleanProperty(true),
                                    new SimpleBooleanProperty(false),
                                    new SimpleBooleanProperty(false),
                                    new SimpleBooleanProperty(false),
                                    new SimpleBooleanProperty(false),
                                    new SimpleBooleanProperty(false),
                                    new SimpleBooleanProperty(false),
                                    new SimpleBooleanProperty(false),
                                    () -> {},
                                    () -> {},
                                    () -> {},
                                    reason -> {},
                                    () -> {},
                                    () -> {});
                    TableView<ActionEditRow> table = session.table();
                    session.dialog().show();
                    table.applyCss();
                    table.layout();

                    assertEquals(
                            WarehouseTaskDialogSupport.SUPPLY_COLUMN_HEADERS,
                            table.getColumns().stream().map(TableColumn::getText).toList());
                    assertEquals("Рама верхняя", withLength.name());
                    assertEquals("Порог 14 мм", nameWithSize.name());
                    assertEquals("10", withLength.referenceQuantityText());
                    assertEquals("0", withLength.receivedQuantityText());
                    assertEquals("10", withLength.remainingQuantityText());

                    TableColumn<ActionEditRow, ?> nameCol = table.getColumns().get(1);
                    assertEquals("Рама верхняя", nameCol.getCellObservableValue(0).getValue());
                    assertEquals("Порог 14 мм", nameCol.getCellObservableValue(1).getValue());
                    String name0 = String.valueOf(nameCol.getCellObservableValue(0).getValue());
                    String name1 = String.valueOf(nameCol.getCellObservableValue(1).getValue());
                    assertFalse(name0.contains("Длина"));
                    assertFalse(name0.contains("868"));
                    assertFalse(name1.contains("Длина"));
                    assertFalse(name1.contains("812"));
                    assertTrue(name1.contains("14 мм"));

                    session.close();
                });
    }

    @Test
    void newReceiveDisablesCellAndQuantityEditorsUntilTake() throws Exception {
        runOnFx(
                () -> {
                    ReceiveAllocationEditRow line =
                            new ReceiveAllocationEditRow(
                                    UUID.randomUUID(),
                                    new WarehouseWorkspaceViewModel.MaterialParts(
                                            "A1", "Name", "C", "S", "шт"),
                                    new BigDecimal("10"),
                                    null,
                                    new BigDecimal("10"));
                    ObservableList<ActionEditRow> lines = FXCollections.observableArrayList(line);
                    SimpleBooleanProperty canTake = new SimpleBooleanProperty(true);
                    SimpleBooleanProperty editorsEnabled = new SimpleBooleanProperty(false);
                    SimpleBooleanProperty canSend = new SimpleBooleanProperty(false);
                    SimpleBooleanProperty canReceive = new SimpleBooleanProperty(false);
                    SimpleBooleanProperty canReject = new SimpleBooleanProperty(false);
                    SimpleBooleanProperty canReturn = new SimpleBooleanProperty(false);
                    SimpleBooleanProperty canPrepare = new SimpleBooleanProperty(false);
                    SimpleBooleanProperty loading = new SimpleBooleanProperty(false);

                    WarehouseTaskDialogSupport.TaskDialogSession session =
                            WarehouseTaskDialogSupport.createTaskDialog(
                                    new TaskRow(
                                            taskView(
                                                    WarehouseTaskKind.TRANSFER_RECEIPT,
                                                    WarehouseTaskState.NEW),
                                            null),
                                    lines,
                                    FXCollections.observableArrayList(),
                                    canTake,
                                    editorsEnabled,
                                    canSend,
                                    canReceive,
                                    canReject,
                                    canReturn,
                                    canPrepare,
                                    loading,
                                    () -> {},
                                    () -> {},
                                    () -> {},
                                    reason -> {},
                                    () -> {},
                                    () -> {});

                    TableView<ActionEditRow> table = session.table();
                    session.dialog().show();
                    table.applyCss();
                    table.layout();
                    assertEquals("Отправлено", session.referenceQuantityColumn().getText());
                    assertEquals("Количество", table.getColumns().get(7).getText());

                    ComboBox<?> cellCombo = findGraphic(table, 6, ComboBox.class);
                    assertNotNull(cellCombo);
                    assertTrue(cellCombo.isDisabled());

                    TableCell<?, ?> qtyCell = cellAt(table, 7);
                    assertNotNull(qtyCell);
                    assertFalse(qtyCell.getGraphic() instanceof TextField);
                    assertEquals(DecimalUiFormat.formatRu(new BigDecimal("10")), qtyCell.getText());

                    editorsEnabled.set(true);
                    table.refresh();
                    table.applyCss();
                    table.layout();

                    ComboBox<?> cellComboAfter = findGraphic(table, 6, ComboBox.class);
                    assertNotNull(cellComboAfter);
                    assertFalse(cellComboAfter.isDisabled());

                    TextField qtyField = findGraphic(table, 7, TextField.class);
                    assertNotNull(qtyField);
                    assertEquals(DecimalUiFormat.formatRu(new BigDecimal("10")), qtyField.getText());

                    qtyField.setText("");
                    qtyField.appendText("1");
                    qtyField.appendText("0");
                    assertEquals("10", qtyField.getText());
                    assertEquals("10", line.quantityTextProperty().get());

                    qtyField.setText("");
                    qtyField.appendText("1");
                    qtyField.appendText("2");
                    qtyField.appendText("3");
                    assertEquals("123", qtyField.getText());

                    session.close();
                });
    }

    @Test
    void hasOperationalActionsOnlyForInboxStates() {
        assertTrue(
                WarehouseTaskDialogSupport.hasOperationalActions(
                        new TaskRow(
                                taskView(
                                        WarehouseTaskKind.TRANSFER_PREPARATION,
                                        WarehouseTaskState.NEW),
                                null)));
        assertTrue(
                WarehouseTaskDialogSupport.hasOperationalActions(
                        new TaskRow(
                                taskView(
                                        WarehouseTaskKind.TRANSFER_RECEIPT,
                                        WarehouseTaskState.IN_WORK),
                                "admin")));
    }

    private static void runOnFx(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(
                () -> {
                    try {
                        action.run();
                    } catch (Throwable throwable) {
                        error.set(throwable);
                    } finally {
                        latch.countDown();
                    }
                });
        assertTrue(latch.await(20, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError(error.get().getMessage(), error.get());
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Node> T findGraphic(TableView<?> table, int columnIndex, Class<T> type) {
        TableCell<?, ?> cell = cellAt(table, columnIndex);
        assertNotNull(cell);
        Node graphic = cell.getGraphic();
        assertInstanceOf(type, graphic);
        return (T) graphic;
    }

    private static TableCell<?, ?> cellAt(TableView<?> table, int columnIndex) {
        table.scrollTo(0);
        table.layout();
        @SuppressWarnings("unchecked")
        TableColumn<ActionEditRow, ?> column =
                (TableColumn<ActionEditRow, ?>) table.getColumns().get(columnIndex);
        for (Node node : table.lookupAll(".table-cell")) {
            if (node instanceof TableCell<?, ?> cell
                    && cell.getTableColumn() == column
                    && cell.getIndex() == 0) {
                return cell;
            }
        }
        return null;
    }


    @Test
    void transferMaterialColumnsPreferWiderArticleAndColor() throws Exception {
        runOnFx(
                () -> {
                    ReceiveAllocationEditRow line =
                            new ReceiveAllocationEditRow(
                                    UUID.randomUUID(),
                                    new WarehouseWorkspaceViewModel.MaterialParts(
                                            "ART-LONG-CODE",
                                            "Very long material name for tooltip",
                                            "Deep azure",
                                            "S",
                                            "шт"),
                                    new BigDecimal("10"),
                                    null,
                                    new BigDecimal("10"));
                    ObservableList<ActionEditRow> lines = FXCollections.observableArrayList(line);
                    WarehouseTaskDialogSupport.TaskDialogSession session =
                            WarehouseTaskDialogSupport.createTaskDialog(
                                    new TaskRow(
                                            taskView(
                                                    WarehouseTaskKind.TRANSFER_PREPARATION,
                                                    WarehouseTaskState.NEW),
                                            null),
                                    lines,
                                    FXCollections.observableArrayList(),
                                    new SimpleBooleanProperty(false),
                                    new SimpleBooleanProperty(false),
                                    new SimpleBooleanProperty(false),
                                    new SimpleBooleanProperty(false),
                                    new SimpleBooleanProperty(false),
                                    new SimpleBooleanProperty(false),
                                    new SimpleBooleanProperty(false),
                                    new SimpleBooleanProperty(false),
                                    () -> {},
                                    () -> {},
                                    () -> {},
                                    reason -> {},
                                    () -> {},
                                    () -> {});
                    TableView<ActionEditRow> table = session.table();
                    session.dialog().show();
                    table.applyCss();
                    table.layout();
                    assertTrue(table.getColumns().get(0).getWidth() >= 100.0);
                    assertTrue(table.getColumns().get(2).getWidth() >= 90.0);
                    // Scan for article text cell with tooltip
                    boolean foundTooltip = false;
                    for (Node node : table.lookupAll(".table-cell")) {
                        if (node instanceof TableCell<?, ?> cell) {
                            Object item = cell.getItem();
                            if ("ART-LONG-CODE".equals(item)) {
                                assertNotNull(cell.getTooltip());
                                assertEquals("ART-LONG-CODE", cell.getTooltip().getText());
                                foundTooltip = true;
                                break;
                            }
                        }
                    }
                    assertTrue(foundTooltip, "expected article tooltip cell");
                    table.getItems().clear();
                    table.refresh();
                    table.applyCss();
                    table.layout();
                    boolean staleTooltip = false;
                    for (Node node : table.lookupAll(".table-cell")) {
                        if (node instanceof TableCell<?, ?> cell
                                && cell.getTableColumn() == table.getColumns().get(0)
                                && cell.getTooltip() != null) {
                            staleTooltip = true;
                        }
                    }
                    assertFalse(staleTooltip, "article tooltip must clear when rows are emptied");
                    session.close();
                });
    }

    private static WarehouseTaskView taskView(WarehouseTaskKind kind, WarehouseTaskState state) {
        UUID documentId = UUID.randomUUID();
        return new WarehouseTaskView(
                WarehouseTaskSource.TRANSFER_DOCUMENT,
                documentId,
                null,
                "TR-1",
                "25096174",
                kind,
                state,
                UUID.randomUUID(),
                "MAIN",
                "Основной склад",
                UUID.randomUUID(),
                "SECOND",
                "Второй склад",
                1,
                state == WarehouseTaskState.IN_WORK ? UUID.randomUUID() : null,
                state == WarehouseTaskState.IN_WORK ? Instant.EPOCH : null,
                Instant.EPOCH,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private static WarehouseTaskView supplyTaskView(WarehouseTaskState state) {
        UUID demandId = UUID.randomUUID();
        return new WarehouseTaskView(
                WarehouseTaskSource.WAREHOUSE_DEMAND,
                null,
                demandId,
                "—",
                null,
                WarehouseTaskKind.PRODUCTION_MATERIAL_SUPPLY,
                state,
                null,
                null,
                null,
                UUID.randomUUID(),
                "PROD",
                "Production",
                1,
                null,
                null,
                Instant.EPOCH,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}

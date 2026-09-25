package com.tmp.ui.shell.screen.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.order.DecimalUiFormat;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.ActionEditRow;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.ReceiveAllocationEditRow;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.TaskRow;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskKind;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskState;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskView;
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
                "Требуется",
                WarehouseTaskDialogSupport.referenceQuantityHeader(
                        WarehouseTaskKind.TRANSFER_PREPARATION));
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
                                        loading,
                                        () -> {},
                                        () -> {},
                                        () -> {},
                                        reason -> {},
                                        () -> {});

                        assertEquals(
                                WarehouseTaskDialogSupport.TITLE, session.dialog().getTitle());
                        assertEquals("Подготовка материалов", session.titleLabel().getText());
                        assertTrue(session.routeLabel().getText().contains("→"));
                        assertEquals("Требуется", session.referenceQuantityColumn().getText());
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
                                    loading,
                                    () -> {},
                                    () -> {},
                                    () -> {},
                                    reason -> {},
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

    private static WarehouseTaskView taskView(WarehouseTaskKind kind, WarehouseTaskState state) {
        UUID documentId = UUID.randomUUID();
        return new WarehouseTaskView(
                documentId,
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
}

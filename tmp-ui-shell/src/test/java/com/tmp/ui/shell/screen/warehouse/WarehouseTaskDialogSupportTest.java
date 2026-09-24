package com.tmp.ui.shell.screen.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.TaskRow;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskKind;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskState;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskView;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
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
    void materialColumnHeadersAreSplit() {
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

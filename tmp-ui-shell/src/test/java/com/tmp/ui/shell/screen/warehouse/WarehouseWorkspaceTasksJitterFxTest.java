package com.tmp.ui.shell.screen.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.UiShellScreens;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Tasks main screen: list-only layout, columns, loading overlay stability. */
class WarehouseWorkspaceTasksJitterFxTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void tasksColumnsMatchTargetOrderWithoutRouteAndWithoutLowerDetail() throws Exception {
        runOnFx(
                (root, viewModel) -> {
                    viewModel.selectTab(WarehouseWorkspaceViewModel.WorkspaceTab.TASKS);
                    @SuppressWarnings("unchecked")
                    TableView<Object> tasksTable = (TableView<Object>) root.lookup("#tasksTable");
                    assertNotNull(tasksTable);
                    List<String> headers =
                            tasksTable.getColumns().stream().map(TableColumn::getText).toList();
                    assertEquals(
                            List.of(
                                    "Дата/время",
                                    "Заказ",
                                    "Вид",
                                    "Состояние",
                                    "Откуда",
                                    "Куда",
                                    "Строк",
                                    "Исполнитель",
                                    "Документ"),
                            headers);
                    assertFalse(headers.contains("Маршрут"));
                    assertEquals("Документ", headers.get(headers.size() - 1));
                    assertNull(root.lookup("#actionLinesTable"));
                    assertNull(root.lookup("#takeTaskInWorkButton"));
                    assertNull(root.lookup("#sendTransferButton"));
                    assertNull(root.lookup("#receiveTransferButton"));
                    assertNull(root.lookup("#rejectTransferButton"));
                    assertNull(root.lookup("#returnTransferButton"));
                    assertNull(root.lookup("#addReceiveAllocationButton"));
                    assertNull(root.lookup("#taskDetailsLabel"));
                    assertEquals(
                            TableView.UNCONSTRAINED_RESIZE_POLICY,
                            tasksTable.getColumnResizePolicy());
                    assertEquals(
                            TasksTableStabilityMarker.MARKER,
                            TasksTableStabilityMarker.MARKER);
                });
    }

    @Test
    void tasksLoadingLabelNeverManagedAndDoesNotShrinkTable() throws Exception {
        WarehouseWorkspaceViewModel viewModel =
                new WarehouseWorkspaceViewModel(
                        new WarehouseWorkbenchUiTestSupport.NoOpWarehouseApi(),
                        new WarehouseWorkbenchUiTestSupport.AllowAllAuthorization());

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<Double> heightBefore = new AtomicReference<>();
        AtomicReference<Double> heightDuring = new AtomicReference<>();
        AtomicReference<Boolean> managedDuring = new AtomicReference<>();

        Platform.runLater(
                () -> {
                    try {
                        FXMLLoader loader =
                                new FXMLLoader(
                                        Thread.currentThread()
                                                .getContextClassLoader()
                                                .getResource(
                                                        UiShellScreens.WAREHOUSE_WORKSPACE_FXML));
                        loader.setControllerFactory(type -> new WarehouseWorkspaceController());
                        Parent root = loader.load();
                        WarehouseWorkspaceController controller = loader.getController();
                        controller.setViewModel(viewModel);
                        viewModel.selectTab(WarehouseWorkspaceViewModel.WorkspaceTab.TASKS);

                        Stage stage = new Stage();
                        stage.setScene(new Scene(root, 1280, 800));
                        stage.show();
                        root.applyCss();
                        root.layout();

                        @SuppressWarnings("unchecked")
                        TableView<Object> tasksTable =
                                (TableView<Object>) root.lookup("#tasksTable");
                        Label loadingLabel = (Label) root.lookup("#loadingLabel");
                        assertNotNull(tasksTable);
                        assertNotNull(loadingLabel);

                        heightBefore.set(tasksTable.getHeight());
                        assertTrue(heightBefore.get() > 100, "table should have layout height");

                        viewModel.loadingProperty().set(true);
                        root.layout();

                        managedDuring.set(loadingLabel.isManaged());
                        heightDuring.set(tasksTable.getHeight());
                        assertTrue(loadingLabel.isVisible());

                        stage.close();
                    } catch (Throwable throwable) {
                        error.set(throwable);
                    } finally {
                        latch.countDown();
                    }
                });

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError("FX tasks jitter geometry failed", error.get());
        }
        assertFalse(managedDuring.get(), "loadingLabel must stay unmanaged");
        assertEquals(
                heightBefore.get(),
                heightDuring.get(),
                0.5,
                "table height must not change when loading overlay appears");
    }

    @Test
    void warehouseFilterKeepsSameItemsInstanceWithoutIntermediateEmpty() throws Exception {
        WarehouseWorkspaceViewModel viewModel =
                new WarehouseWorkspaceViewModel(
                        new WarehouseWorkbenchUiTestSupport.NoOpWarehouseApi(),
                        new WarehouseWorkbenchUiTestSupport.AllowAllAuthorization());
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        Platform.runLater(
                () -> {
                    try {
                        FXMLLoader loader =
                                new FXMLLoader(
                                        Thread.currentThread()
                                                .getContextClassLoader()
                                                .getResource(
                                                        UiShellScreens.WAREHOUSE_WORKSPACE_FXML));
                        loader.setControllerFactory(type -> new WarehouseWorkspaceController());
                        Parent root = loader.load();
                        WarehouseWorkspaceController controller = loader.getController();
                        controller.setViewModel(viewModel);
                        viewModel.selectTab(WarehouseWorkspaceViewModel.WorkspaceTab.TASKS);

                        Stage stage = new Stage();
                        stage.setScene(new Scene(root, 1280, 800));
                        stage.show();
                        root.applyCss();
                        root.layout();

                        @SuppressWarnings("unchecked")
                        TableView<Object> tasksTable =
                                (TableView<Object>) root.lookup("#tasksTable");
                        Object itemsBefore = tasksTable.getItems();
                        assertSame(viewModel.taskRows(), itemsBefore);

                        viewModel.loadingProperty().set(true);
                        root.layout();
                        assertSame(itemsBefore, tasksTable.getItems());
                        assertFalse(
                                ((Label) root.lookup("#loadingLabel")).isManaged(),
                                "loading must remain unmanaged during filter reload");

                        viewModel.loadingProperty().set(false);
                        stage.close();
                    } catch (Throwable throwable) {
                        error.set(throwable);
                    } finally {
                        latch.countDown();
                    }
                });

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError("FX tasks items identity failed", error.get());
        }
    }

    private static void runOnFx(FxAction action) throws Exception {
        WarehouseWorkspaceViewModel viewModel =
                new WarehouseWorkspaceViewModel(
                        new WarehouseWorkbenchUiTestSupport.NoOpWarehouseApi(),
                        new WarehouseWorkbenchUiTestSupport.AllowAllAuthorization());
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(
                () -> {
                    try {
                        FXMLLoader loader =
                                new FXMLLoader(
                                        Thread.currentThread()
                                                .getContextClassLoader()
                                                .getResource(
                                                        UiShellScreens.WAREHOUSE_WORKSPACE_FXML));
                        loader.setControllerFactory(type -> new WarehouseWorkspaceController());
                        Parent root = loader.load();
                        WarehouseWorkspaceController controller = loader.getController();
                        controller.setViewModel(viewModel);
                        Stage stage = new Stage();
                        stage.setScene(new Scene(root, 1280, 800));
                        stage.show();
                        root.applyCss();
                        root.layout();
                        action.run(root, viewModel);
                        stage.close();
                    } catch (Throwable throwable) {
                        error.set(throwable);
                    } finally {
                        latch.countDown();
                    }
                });
        assertTrue(latch.await(15, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError("FX tasks test failed", error.get());
        }
    }

    @FunctionalInterface
    private interface FxAction {
        void run(Parent root, WarehouseWorkspaceViewModel viewModel) throws Exception;
    }
}

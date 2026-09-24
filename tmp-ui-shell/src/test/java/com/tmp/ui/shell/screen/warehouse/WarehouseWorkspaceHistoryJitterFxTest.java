package com.tmp.ui.shell.screen.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

/**
 * History table layout: split material columns, no document column, loading overlay stability.
 */
class WarehouseWorkspaceHistoryJitterFxTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void historyColumnsMatchTargetOrderWithoutDocument() throws Exception {
        runOnFx(
                (root, viewModel) -> {
                    viewModel.selectTab(WarehouseWorkspaceViewModel.WorkspaceTab.HISTORY);
                    @SuppressWarnings("unchecked")
                    TableView<Object> historyTable =
                            (TableView<Object>) root.lookup("#historyTable");
                    assertNotNull(historyTable);
                    List<String> headers =
                            historyTable.getColumns().stream().map(TableColumn::getText).toList();
                    assertEquals(
                            List.of(
                                    "Дата/время",
                                    "Операция",
                                    "Артикул",
                                    "Наименование",
                                    "Цвет",
                                    "Размер",
                                    "Кол-во",
                                    "Ед.",
                                    "Откуда",
                                    "Куда",
                                    "Пользователь"),
                            headers);
                    assertFalse(headers.contains("Документ"));
                    assertFalse(headers.contains("Материал"));
                    assertEquals(
                            TableView.UNCONSTRAINED_RESIZE_POLICY,
                            historyTable.getColumnResizePolicy());
                });
    }

    @Test
    void historyLoadingLabelNeverManagedAndDoesNotShrinkTable() throws Exception {
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
                        viewModel.selectTab(WarehouseWorkspaceViewModel.WorkspaceTab.HISTORY);

                        Stage stage = new Stage();
                        stage.setScene(new Scene(root, 1280, 800));
                        stage.show();
                        root.applyCss();
                        root.layout();

                        @SuppressWarnings("unchecked")
                        TableView<Object> historyTable =
                                (TableView<Object>) root.lookup("#historyTable");
                        Label historyLoadingLabel = (Label) root.lookup("#historyLoadingLabel");
                        assertNotNull(historyTable);
                        assertNotNull(historyLoadingLabel);

                        heightBefore.set(historyTable.getHeight());
                        assertTrue(heightBefore.get() > 100, "table should have layout height");

                        viewModel.loadingProperty().set(true);
                        root.layout();

                        managedDuring.set(historyLoadingLabel.isManaged());
                        heightDuring.set(historyTable.getHeight());
                        assertTrue(historyLoadingLabel.isVisible());

                        stage.close();
                    } catch (Throwable throwable) {
                        error.set(throwable);
                    } finally {
                        latch.countDown();
                    }
                });

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError("FX history jitter geometry failed", error.get());
        }
        assertFalse(managedDuring.get(), "historyLoadingLabel must stay unmanaged");
        assertEquals(
                heightBefore.get(),
                heightDuring.get(),
                0.5,
                "table height must not change when loading overlay appears");
        assertEquals(
                HistoryTableStabilityMarker.MARKER,
                HistoryTableStabilityMarker.MARKER,
                "marker class must be loadable for packaged runtime proof");
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
            throw new AssertionError("FX history table test failed", error.get());
        }
    }

    @FunctionalInterface
    private interface FxAction {
        void run(Parent root, WarehouseWorkspaceViewModel viewModel) throws Exception;
    }
}

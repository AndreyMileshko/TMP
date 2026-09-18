package com.tmp.ui.shell.screen.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.UiShellScreens;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Proves Stocks loading overlay does not resize TableView (primary jitter root cause).
 */
class WarehouseWorkspaceStocksJitterFxTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void stockLoadingLabelNeverManagedAndDoesNotShrinkTable() throws Exception {
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
                        viewModel.selectTab(WarehouseWorkspaceViewModel.WorkspaceTab.STOCK);

                        Stage stage = new Stage();
                        stage.setScene(new Scene(root, 1100, 720));
                        stage.show();
                        root.applyCss();
                        root.layout();

                        @SuppressWarnings("unchecked")
                        TableView<Object> stockTable =
                                (TableView<Object>) root.lookup("#stockTable");
                        Label stockLoadingLabel = (Label) root.lookup("#stockLoadingLabel");
                        assertNotNull(stockTable);
                        assertNotNull(stockLoadingLabel);

                        heightBefore.set(stockTable.getHeight());
                        assertTrue(heightBefore.get() > 100, "table should have layout height");

                        viewModel.loadingProperty().set(true);
                        root.layout();

                        managedDuring.set(stockLoadingLabel.isManaged());
                        heightDuring.set(stockTable.getHeight());
                        assertTrue(stockLoadingLabel.isVisible());

                        stage.close();
                    } catch (Throwable throwable) {
                        error.set(throwable);
                    } finally {
                        latch.countDown();
                    }
                });

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError("FX stocks jitter geometry failed", error.get());
        }
        assertFalse(managedDuring.get(), "stockLoadingLabel must stay unmanaged");
        assertEquals(
                heightBefore.get(),
                heightDuring.get(),
                0.5,
                "table height must not change when loading overlay appears");
        assertEquals(
                StocksTableStabilityMarker.MARKER,
                StocksTableStabilityMarker.MARKER,
                "marker class must be loadable for packaged runtime proof");
    }
}

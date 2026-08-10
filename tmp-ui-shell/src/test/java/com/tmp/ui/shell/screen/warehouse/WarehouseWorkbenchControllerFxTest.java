package com.tmp.ui.shell.screen.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WarehouseWorkbenchControllerFxTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void stockTableExposesExtendedMaterialColumns() throws Exception {
        WarehouseWorkbenchViewModel viewModel =
                new WarehouseWorkbenchViewModel(
                        new WarehouseWorkbenchUiTestSupport.NoOpWarehouseApi(),
                        new WarehouseWorkbenchUiTestSupport.AllowAllAuthorization());

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<String> columnTexts = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                FXMLLoader loader =
                        new FXMLLoader(
                                Thread.currentThread()
                                        .getContextClassLoader()
                                        .getResource(UiShellScreens.WAREHOUSE_WORKBENCH_FXML));
                loader.setControllerFactory(type -> new WarehouseWorkbenchController());
                Parent root = loader.load();
                WarehouseWorkbenchController controller = loader.getController();
                controller.setViewModel(viewModel);
                Stage stage = new Stage();
                stage.setScene(new Scene(root));
                @SuppressWarnings("unchecked")
                TableView<Object> stockTable = (TableView<Object>) root.lookup("#stockTable");
                assertNotNull(stockTable);
                columnTexts.set(
                        stockTable.getColumns().stream()
                                .map(TableColumn::getText)
                                .reduce((left, right) -> left + "|" + right)
                                .orElse(""));
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError("Warehouse workbench FX load failed", error.get());
        }
        assertEquals(
                "Артикул|Наименование|Цвет|Размер|Ед. изм.|Склад|Ячейка|Количество|Состояние",
                columnTexts.get());
    }
}

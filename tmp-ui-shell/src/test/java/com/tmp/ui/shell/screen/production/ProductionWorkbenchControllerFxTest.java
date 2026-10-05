package com.tmp.ui.shell.screen.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TreeTableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ProductionWorkbenchControllerFxTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void fxmlLoadsOrderCardWithModeRadiosAndBackButton() throws Exception {
        ProductionWorkbenchViewModel viewModel =
                new ProductionWorkbenchViewModel(
                        new ProductionWorkbenchUiTestSupport.StubQueryApi(),
                        new ProductionWorkbenchUiTestSupport.StubApplicationApi(),
                        new ProductionWorkbenchUiTestSupport.StubOrderQuery(),
                        new ProductionWorkbenchUiTestSupport.StubWorklistQuery(),
                        new ProductionWorkbenchUiTestSupport.StubWarehouseApi(),
                        new ProductionWorkbenchUiTestSupport.AllowAllAuthorization(),
                        new ProductionWorkbenchUiTestSupport.StubAuthentication());

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<Boolean> ok = new AtomicReference<>(false);

        Platform.runLater(
                () -> {
                    try {
                        Parent root = loadRoot();
                        root.applyCss();
                        root.layout();

                        assertNotNull(root.lookup("#productionTree"));
                        assertNotNull(root.lookup("#detailPane"));
                        assertNotNull(root.lookup("#acceptButton"));
                        assertEquals(
                                "Принять в производство",
                                ((Button) root.lookup("#acceptButton")).getText());
                        assertEquals(
                                "← К производству",
                                ((Button) root.lookup("#backToTreeButton")).getText());
                        assertNotNull(root.lookup("#standardModeRadio"));
                        assertNotNull(root.lookup("#flexibleModeRadio"));
                        assertEquals(
                                "Стандартный",
                                ((RadioButton) root.lookup("#standardModeRadio")).getText());
                        assertEquals(
                                "Гибкий",
                                ((RadioButton) root.lookup("#flexibleModeRadio")).getText());
                        assertNotNull(root.lookup("#saveQuantityModeButton"));
                        assertNotNull(root.lookup("#materialsBlock"));
                        assertNotNull(root.lookup("#historyBlock"));
                        assertNotNull(root.lookup("#releaseButton"));
                        assertEquals(
                                "Выпустить", ((Button) root.lookup("#releaseButton")).getText());
                        assertNull(root.lookup("#materialRequirementPanel"));
                        assertNull(root.lookup("#prepareTransferButton"));
                        assertNull(root.lookup("#prepareReleaseButton"));
                        assertNull(root.lookup("#confirmReceiptButton"));
                        assertNull(root.lookup("#logicalTransferCombo"));

                        TabPane tabs = (TabPane) root.lookup("#orderCardTabs");
                        assertNotNull(tabs);
                        assertEquals(2, tabs.getTabs().size());
                        assertEquals("Обзор", tabs.getTabs().get(0).getText());
                        assertTrue(tabs.getTabs().get(1).getText().startsWith("Позиции"));
                        assertEquals(tabs.getTabs().get(0), tabs.getSelectionModel().getSelectedItem());

                        @SuppressWarnings("unchecked")
                        TableView<ProductionItemRow> itemsTable =
                                (TableView<ProductionItemRow>) root.lookup("#itemsTable");
                        assertNotNull(itemsTable);
                        assertEquals(
                                List.of(
                                        "Позиция",
                                        "Изделие",
                                        "Кол-во",
                                        "Состояние",
                                        "Изготовлено",
                                        "Осталось"),
                                itemsTable.getColumns().stream()
                                        .map(TableColumn::getText)
                                        .toList());
                        assertTrue(itemsTable.getPlaceholder() instanceof Label);
                        assertEquals("Нет данных", ((Label) itemsTable.getPlaceholder()).getText());
                        @SuppressWarnings("unchecked")
                        TreeTableView<?> tree =
                                (TreeTableView<?>) root.lookup("#productionTree");
                        assertTrue(tree.getPlaceholder() instanceof Label);
                        assertEquals("Нет данных", ((Label) tree.getPlaceholder()).getText());
                        ok.set(true);
                    } catch (Throwable throwable) {
                        error.set(throwable);
                    } finally {
                        latch.countDown();
                    }
                });

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError("Production workbench FX load failed", error.get());
        }
        assertTrue(ok.get());
        assertFalse(viewModel.canAcceptProperty().get());
    }

    private static Parent loadRoot() throws Exception {
        FXMLLoader loader =
                new FXMLLoader(
                        Thread.currentThread()
                                .getContextClassLoader()
                                .getResource(UiShellScreens.PRODUCTION_WORKBENCH_FXML));
        loader.setControllerFactory(type -> new ProductionWorkbenchController());
        Parent root = loader.load();
        ProductionWorkbenchController controller = loader.getController();
        controller.setViewModel(
                new ProductionWorkbenchViewModel(
                        new ProductionWorkbenchUiTestSupport.StubQueryApi(),
                        new ProductionWorkbenchUiTestSupport.StubApplicationApi(),
                        new ProductionWorkbenchUiTestSupport.StubOrderQuery(),
                        new ProductionWorkbenchUiTestSupport.StubWorklistQuery(),
                        new ProductionWorkbenchUiTestSupport.StubWarehouseApi(),
                        new ProductionWorkbenchUiTestSupport.AllowAllAuthorization(),
                        new ProductionWorkbenchUiTestSupport.StubAuthentication()));
        Stage stage = new Stage();
        stage.setScene(new Scene(root, 1200, 800));
        stage.show();
        return root;
    }
}

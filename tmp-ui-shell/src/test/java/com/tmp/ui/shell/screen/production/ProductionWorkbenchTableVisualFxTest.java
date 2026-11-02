package com.tmp.ui.shell.screen.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.production.api.ProductionQueryApi.ItemProductionStateStatus;
import com.tmp.production.api.ProductionQueryApi.OrderProductionViewStatus;
import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.ui.shell.theme.TmpTheme;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.HBox;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** FX regression for Production Workbench LEVEL 1 table UX polish. */
class ProductionWorkbenchTableVisualFxTest {

    private static final UUID ORDER = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID ITEM = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void fxmlColumnPresenceOrderAndWidthsMatchPolishContract() throws Exception {
        String fxml;
        try (var in =
                ProductionWorkbenchController.class.getResourceAsStream(
                        "ProductionWorkbenchScreen.fxml")) {
            assertNotNull(in);
            fxml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(fxml.contains("styleClass=\"tmp-production-workbench-tree\""));
        assertTrue(fxml.contains("fx:id=\"treeSelectedColumn\""));
        assertTrue(fxml.contains("fx:id=\"treeIdentityColumn\" text=\"Заказ / Позиция\""));
        assertTrue(fxml.contains("fx:id=\"treeSecondaryColumn\" text=\"Изделие / Заказчик\""));
        assertTrue(fxml.contains("fx:id=\"treeQuantityColumn\" text=\"Количество\""));
        assertTrue(fxml.contains("fx:id=\"treeStatusColumn\" text=\"Состояние\""));
        assertTrue(fxml.contains("fx:id=\"treeReleasedColumn\" text=\"Изготовлено\""));
        assertTrue(fxml.contains("fx:id=\"treeRemainingColumn\" text=\"Осталось\""));
        assertTrue(fxml.contains("fx:id=\"treeMaterialRequestsColumn\" text=\"Запросы материалов\""));

        int selected = fxml.indexOf("fx:id=\"treeSelectedColumn\"");
        int identity = fxml.indexOf("fx:id=\"treeIdentityColumn\"");
        int secondary = fxml.indexOf("fx:id=\"treeSecondaryColumn\"");
        int quantity = fxml.indexOf("fx:id=\"treeQuantityColumn\"");
        int status = fxml.indexOf("fx:id=\"treeStatusColumn\"");
        int released = fxml.indexOf("fx:id=\"treeReleasedColumn\"");
        int remaining = fxml.indexOf("fx:id=\"treeRemainingColumn\"");
        int materialRequests = fxml.indexOf("fx:id=\"treeMaterialRequestsColumn\"");
        assertTrue(
                selected >= 0
                        && identity > selected
                        && secondary > identity
                        && quantity > secondary
                        && status > quantity
                        && released > status
                        && remaining > released
                        && materialRequests > remaining);

        assertTrue(fxml.contains("prefWidth=\"64\""));
        assertTrue(fxml.contains("prefWidth=\"140\""));
        assertTrue(fxml.contains("prefWidth=\"200\""));
        assertTrue(fxml.contains("prefWidth=\"200\""));
    }

    @Test
    void treeAppliesRowClassesStatusDotsSelectionAndCheckbox() throws Exception {
        runOnFx(
                () -> {
                    Parent root = loadWorkbench();
                    @SuppressWarnings("unchecked")
                    TreeTableView<ProductionTreeNode> tree =
                            (TreeTableView<ProductionTreeNode>) root.lookup("#productionTree");
                    assertNotNull(tree);
                    assertTrue(
                            tree.getStyleClass()
                                    .contains(ProductionWorkbenchVisualStyles.TREE_STYLE));
                    assertEquals(8, tree.getColumns().size());
                    assertEquals(
                            List.of(
                                    "",
                                    "Заказ / Позиция",
                                    "Изделие / Заказчик",
                                    "Количество",
                                    "Состояние",
                                    "Изготовлено",
                                    "Осталось",
                                    "Запросы материалов"),
                            tree.getColumns().stream().map(TreeTableColumn::getText).toList());

                    assertEquals(64, tree.getColumns().get(0).getPrefWidth(), 0.1);
                    assertEquals(ProductionWorkbenchVisualStyles.COL_IDENTITY_PREF, tree.getColumns().get(1).getPrefWidth(), 0.1);
                    assertTrue(tree.getColumns().get(2).getPrefWidth() >= ProductionWorkbenchVisualStyles.COL_SECONDARY_MIN);
                    assertEquals(ProductionWorkbenchVisualStyles.COL_QUANTITY_PREF, tree.getColumns().get(3).getPrefWidth(), 0.1);
                    assertEquals(ProductionWorkbenchVisualStyles.COL_STATUS_PREF, tree.getColumns().get(4).getPrefWidth(), 0.1);
                    assertEquals(ProductionWorkbenchVisualStyles.COL_RELEASED_PREF, tree.getColumns().get(5).getPrefWidth(), 0.1);
                    assertEquals(ProductionWorkbenchVisualStyles.COL_REMAINING_PREF, tree.getColumns().get(6).getPrefWidth(), 0.1);
                    assertEquals(ProductionWorkbenchVisualStyles.COL_MATERIAL_REQUESTS_PREF, tree.getColumns().get(7).getPrefWidth(), 0.1);

                    ProductionTreeNode order =
                            ProductionTreeNode.order(
                                    ORDER,
                                    "25096174",
                                    "ООО Заказчик",
                                    OrderProductionViewStatus.NOT_ACCEPTED,
                                    "—",
                                    "—",
                                    "—",
                                    "—",
                                    List.of(new ProductionOrderItemRef(ORDER, ITEM)));
                    ProductionTreeNode item =
                            ProductionTreeNode.item(
                                    ORDER,
                                    ITEM,
                                    "Поз. 1",
                                    "Окно ПВХ",
                                    "6",
                                    ItemProductionStateStatus.IN_PRODUCTION,
                                    "0",
                                    "6",
                                    "—",
                                    1,
                                    true);

                    TreeItem<ProductionTreeNode> rootItem = new TreeItem<>();
                    TreeItem<ProductionTreeNode> orderItem = new TreeItem<>(order);
                    orderItem.setExpanded(true);
                    orderItem.getChildren().add(new TreeItem<>(item));
                    rootItem.getChildren().add(orderItem);
                    tree.setRoot(rootItem);
                    tree.setShowRoot(false);
                    tree.refresh();
                    tree.applyCss();
                    tree.layout();

                    @SuppressWarnings("unchecked")
                    TreeTableRow<ProductionTreeNode> orderRow =
                            (TreeTableRow<ProductionTreeNode>)
                                    tree.lookup(".tree-table-row-cell");
                    assertNotNull(orderRow);
                    assertTrue(
                            orderRow
                                    .getStyleClass()
                                    .contains(ProductionWorkbenchVisualStyles.ROW_ORDER));

                    tree.getSelectionModel().select(0);
                    tree.applyCss();
                    assertTrue(orderRow.isSelected());
                    assertTrue(
                            orderRow
                                    .getStyleClass()
                                    .contains(ProductionWorkbenchVisualStyles.ROW_ORDER));

                    tree.getSelectionModel().clearSelection();
                    orderItem.setExpanded(true);
                    tree.refresh();
                    tree.applyCss();
                    tree.layout();

                    boolean sawItemRow = false;
                    boolean sawStatusDot = false;
                    boolean sawCheckbox = false;
                    for (var node : tree.lookupAll(".tree-table-row-cell")) {
                        @SuppressWarnings("unchecked")
                        TreeTableRow<ProductionTreeNode> row =
                                (TreeTableRow<ProductionTreeNode>) node;
                        if (row.getItem() != null && row.getItem().isItem()) {
                            sawItemRow = true;
                            assertTrue(
                                    row.getStyleClass()
                                            .contains(ProductionWorkbenchVisualStyles.ROW_ITEM));
                        }
                    }
                    for (var node : tree.lookupAll(".tmp-status-cell")) {
                        HBox box = assertInstanceOf(HBox.class, node);
                        assertInstanceOf(Circle.class, box.getChildren().get(0));
                        Label caption = assertInstanceOf(Label.class, box.getChildren().get(1));
                        Circle dot = (Circle) box.getChildren().get(0);
                        if ("Ожидает производства".equals(caption.getText())) {
                            sawStatusDot = true;
                            assertTrue(
                                    dot.getStyleClass()
                                            .contains(
                                                    ProductionWorkbenchVisualStyles
                                                            .STATUS_AWAITING));
                        }
                        if ("В производстве".equals(caption.getText())) {
                            assertTrue(
                                    dot.getStyleClass()
                                            .contains(
                                                    ProductionWorkbenchVisualStyles
                                                            .STATUS_IN_PRODUCTION));
                        }
                    }
                    for (var node : tree.lookupAll(".check-box")) {
                        assertInstanceOf(CheckBox.class, node);
                        sawCheckbox = true;
                    }
                    assertTrue(sawItemRow);
                    assertTrue(sawStatusDot);
                    assertTrue(sawCheckbox);

                    // Empty rows must not keep order/item pastel classes.
                    tree.setRoot(new TreeItem<>());
                    tree.refresh();
                    tree.applyCss();
                    tree.layout();
                    for (var node : tree.lookupAll(".tree-table-row-cell")) {
                        @SuppressWarnings("unchecked")
                        TreeTableRow<ProductionTreeNode> row =
                                (TreeTableRow<ProductionTreeNode>) node;
                        if (row.isEmpty() || row.getItem() == null) {
                            assertFalse(
                                    row.getStyleClass()
                                            .contains(ProductionWorkbenchVisualStyles.ROW_ORDER));
                            assertFalse(
                                    row.getStyleClass()
                                            .contains(ProductionWorkbenchVisualStyles.ROW_ITEM));
                        }
                    }
                });
    }

    private static Parent loadWorkbench() throws Exception {
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
        Scene scene = new Scene(root, 1280, 800);
        TmpTheme.apply(scene);
        TmpTheme.addStylesheet(scene, ProductionWorkbenchVisualStyles.STYLESHEET);
        stage.setScene(scene);
        stage.show();
        root.applyCss();
        root.layout();
        return root;
    }

    private static void runOnFx(FxBody body) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(
                () -> {
                    try {
                        body.run();
                    } catch (Throwable throwable) {
                        error.set(throwable);
                    } finally {
                        latch.countDown();
                    }
                });
        assertTrue(latch.await(20, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError("Production workbench visual FX test failed", error.get());
        }
    }

    @FunctionalInterface
    private interface FxBody {
        void run() throws Exception;
    }
}

package com.tmp.ui.shell.screen.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.ui.shell.theme.TmpTheme;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** FX layout: Production Workbench tree columns fill width; headers stay readable. */
class ProductionWorkbenchColumnLayoutFxTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void columnsFillAvailableWidthAtRepresentativeDesktopSizes() throws Exception {
        runOnFx(
                () -> {
                    assertLayoutAtWidth(1024);
                    assertLayoutAtWidth(1280);
                    assertLayoutAtWidth(1650);
                });
    }

    private static void assertLayoutAtWidth(double sceneWidth) throws Exception {
        Parent root = loadWorkbench(sceneWidth);
        @SuppressWarnings("unchecked")
        TreeTableView<ProductionTreeNode> tree =
                (TreeTableView<ProductionTreeNode>) root.lookup("#productionTree");
        assertTrue(tree != null);

        double treeWidth = Math.max(700, sceneWidth - 64);
        tree.setMinWidth(treeWidth);
        tree.setPrefWidth(treeWidth);
        tree.setMaxWidth(treeWidth);
        // Nudge width so the redistribute listener runs synchronously on this FX thread.
        tree.resize(treeWidth - 1, Math.max(200, tree.getHeight()));
        tree.resize(treeWidth, Math.max(200, tree.getHeight()));
        root.applyCss();
        root.layout();
        tree.applyCss();
        tree.layout();

        double actualTreeWidth = tree.getWidth();
        assertTrue(actualTreeWidth > 0, "tree width must be realized at " + sceneWidth);

        List<TreeTableColumn<ProductionTreeNode, ?>> cols = tree.getColumns();
        assertEquals(8, cols.size());
        assertEquals("", cols.get(0).getText());
        assertEquals("Заказ / Позиция", cols.get(1).getText());
        assertEquals("Изделие / Заказчик", cols.get(2).getText());
        assertEquals("Количество", cols.get(3).getText());
        assertEquals("Состояние", cols.get(4).getText());
        assertEquals("Изготовлено", cols.get(5).getText());
        assertEquals("Осталось", cols.get(6).getText());
        assertEquals("Запросы материалов", cols.get(7).getText());

        assertEquals(
                ProductionWorkbenchVisualStyles.COL_CHECKBOX_PREF, cols.get(0).getPrefWidth(), 0.1);
        assertEquals(
                ProductionWorkbenchVisualStyles.COL_IDENTITY_PREF, cols.get(1).getPrefWidth(), 0.1);
        assertEquals(
                ProductionWorkbenchVisualStyles.COL_QUANTITY_PREF, cols.get(3).getPrefWidth(), 0.1);
        assertEquals(
                ProductionWorkbenchVisualStyles.COL_STATUS_PREF, cols.get(4).getPrefWidth(), 0.1);
        assertEquals(
                ProductionWorkbenchVisualStyles.COL_RELEASED_PREF, cols.get(5).getPrefWidth(), 0.1);
        assertEquals(
                ProductionWorkbenchVisualStyles.COL_REMAINING_PREF, cols.get(6).getPrefWidth(), 0.1);
        assertEquals(
                ProductionWorkbenchVisualStyles.COL_MATERIAL_REQUESTS_PREF,
                cols.get(7).getPrefWidth(),
                0.1);

        double fixed =
                cols.get(0).getPrefWidth()
                        + cols.get(1).getPrefWidth()
                        + cols.get(3).getPrefWidth()
                        + cols.get(4).getPrefWidth()
                        + cols.get(5).getPrefWidth()
                        + cols.get(6).getPrefWidth()
                        + cols.get(7).getPrefWidth();
        double expectedSecondary =
                ProductionWorkbenchVisualStyles.secondaryFlexPrefWidth(actualTreeWidth, fixed);
        assertEquals(
                expectedSecondary,
                cols.get(2).getPrefWidth(),
                1.0,
                "secondary flex mismatch at sceneWidth=" + sceneWidth);

        double used = 0;
        for (TreeTableColumn<ProductionTreeNode, ?> col : cols) {
            used += col.getWidth() > 1 ? col.getWidth() : col.getPrefWidth();
        }
        double unused = actualTreeWidth - used;
        double minFitWidth =
                fixed
                        + ProductionWorkbenchVisualStyles.COL_SECONDARY_MIN
                        + ProductionWorkbenchVisualStyles.TREE_VERTICAL_SCROLL_GUTTER;
        if (actualTreeWidth >= minFitWidth) {
            assertTrue(
                    unused <= ProductionWorkbenchVisualStyles.TREE_MAX_UNUSED_TRAILING,
                    "unused trailing space too large at sceneWidth="
                            + sceneWidth
                            + " treeWidth="
                            + actualTreeWidth
                            + " used="
                            + used
                            + " unused="
                            + unused);
            assertTrue(unused >= -2.0, "columns should not overflow table width");
            assertTrue(
                    cols.get(2).getPrefWidth()
                            > ProductionWorkbenchVisualStyles.COL_MATERIAL_REQUESTS_PREF,
                    "secondary flex must remain wider than material-requests");
            if (actualTreeWidth >= 1500) {
                assertTrue(
                        cols.get(2).getPrefWidth() > cols.get(6).getPrefWidth() * 3,
                        "at wide desktops secondary flex must dominate leftover space");
            }
        } else {
            assertEquals(
                    ProductionWorkbenchVisualStyles.COL_SECONDARY_MIN,
                    cols.get(2).getPrefWidth(),
                    0.1,
                    "narrow viewport keeps secondary at minimum; horizontal scroll expected");
        }

        assertHeaderReadable(cols.get(3), "Количество");
        assertHeaderReadable(cols.get(5), "Изготовлено");
        assertHeaderReadable(cols.get(6), "Осталось");
        assertHeaderReadable(cols.get(7), "Запросы материалов");

        Stage stage = (Stage) root.getScene().getWindow();
        stage.close();
    }

    private static void assertHeaderReadable(
            TreeTableColumn<ProductionTreeNode, ?> column, String expectedText) {
        assertEquals(expectedText, column.getText());
        assertTrue(
                column.getPrefWidth() >= column.getMinWidth(),
                expectedText + " pref must respect min");
        assertTrue(
                column.getPrefWidth() >= expectedText.length() * 7.0,
                expectedText + " column too narrow for header text");
        assertTrue(column.getPrefWidth() >= 90, expectedText + " must exceed old truncated width");
        assertFalse(column.getPrefWidth() <= 88, expectedText + " must not use old max 88");
    }

    private static Parent loadWorkbench(double sceneWidth) throws Exception {
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
        Scene scene = new Scene(root, sceneWidth, 800);
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
        assertTrue(latch.await(60, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError("column layout FX test failed", error.get());
        }
    }

    @FunctionalInterface
    private interface FxBody {
        void run() throws Exception;
    }
}
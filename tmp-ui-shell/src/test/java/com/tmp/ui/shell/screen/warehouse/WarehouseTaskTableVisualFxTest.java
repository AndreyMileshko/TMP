package com.tmp.ui.shell.screen.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.order.worklist.OperationalStatusIndicator;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.TaskRow;
import com.tmp.ui.shell.theme.TmpTheme;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskKind;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskSource;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskState;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskView;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.TableCell;
import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** FX regression: task row style reuse + selected-row distinguishability classes. */
class WarehouseTaskTableVisualFxTest {

    private static final UUID SRC = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID DST = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void recycledTableRowDropsPreviousTaskStyle() throws Exception {
        runOnFx(
                () -> {
                    TableView<TaskRow> table = new TableView<>();
                    TableColumn<TaskRow, String> col = new TableColumn<>("kind");
                    table.getColumns().add(col);
                    table.setRowFactory(
                            tv ->
                                    new TableRow<>() {
                                        @Override
                                        protected void updateItem(TaskRow item, boolean empty) {
                                            super.updateItem(item, empty);
                                            WarehouseTaskVisualStyles.clearRowStyleClasses(this);
                                            if (empty || item == null) {
                                                return;
                                            }
                                            getStyleClass()
                                                    .add(
                                                            WarehouseTaskVisualStyles.rowStyleClass(
                                                                    item.taskKind()));
                                        }
                                    });

                    TaskRow supply = new TaskRow(supplyTask(), null);
                    TaskRow ret =
                            new TaskRow(task(WarehouseTaskKind.RETURN_MATERIALS), null);
                    table.setItems(FXCollections.observableArrayList(supply));

                    Scene scene = new Scene(table, 400, 200);
                    TmpTheme.apply(scene);
                    TmpTheme.addStylesheet(scene, WarehouseTaskVisualStyles.STYLESHEET);
                    Stage stage = new Stage();
                    stage.setScene(scene);
                    stage.show();
                    table.applyCss();
                    table.layout();

                    @SuppressWarnings("unchecked")
                    TableRow<TaskRow> row =
                            (TableRow<TaskRow>) table.lookup(".table-row-cell");
                    assertTrue(row.getStyleClass().contains(WarehouseTaskVisualStyles.ROW_SUPPLY));

                    table.setItems(FXCollections.observableArrayList(ret));
                    table.refresh();
                    table.applyCss();
                    table.layout();

                    @SuppressWarnings("unchecked")
                    TableRow<TaskRow> reused =
                            (TableRow<TaskRow>) table.lookup(".table-row-cell");
                    assertFalse(
                            reused.getStyleClass().contains(WarehouseTaskVisualStyles.ROW_SUPPLY));
                    assertTrue(
                            reused.getStyleClass().contains(WarehouseTaskVisualStyles.ROW_RETURN));

                    table.getSelectionModel().select(0);
                    table.applyCss();
                    assertTrue(reused.isSelected());
                    assertTrue(
                            reused.getStyleClass().contains(WarehouseTaskVisualStyles.ROW_RETURN));

                    stage.close();
                });
    }

    @Test
    void statusIndicatorUsesThemeDotClasses() throws Exception {
        runOnFx(
                () -> {
                    HBox neu =
                            OperationalStatusIndicator.create(
                                    "Новая", WarehouseTaskVisualStyles.STATUS_NEW);
                    HBox work =
                            OperationalStatusIndicator.create(
                                    "В работе", WarehouseTaskVisualStyles.STATUS_IN_WORK);
                    assertTrue(neu.getStyleClass().contains("tmp-status-cell"));
                    assertEquals(
                            "Новая",
                            ((javafx.scene.control.Label) neu.getChildren().get(1)).getText());
                    assertTrue(
                            neu.getChildren()
                                    .get(0)
                                    .getStyleClass()
                                    .contains(WarehouseTaskVisualStyles.STATUS_NEW));
                    assertTrue(
                            work.getChildren()
                                    .get(0)
                                    .getStyleClass()
                                    .contains(WarehouseTaskVisualStyles.STATUS_IN_WORK));
                });
    }

    @Test
    void tasksFxmlDropsDocumentColumnAndAddsRouteArrowBetweenWarehouses() throws Exception {
        String fxml;
        try (var in =
                WarehouseWorkspaceController.class.getResourceAsStream(
                        "WarehouseWorkspaceScreen.fxml")) {
            assertTrue(in != null);
            fxml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertFalse(fxml.contains("fx:id=\"taskDocumentColumn\""));
        assertFalse(fxml.contains("text=\"Документ\""));
        assertTrue(fxml.contains("fx:id=\"taskRouteArrowColumn\""));
        int source = fxml.indexOf("fx:id=\"taskSourceColumn\"");
        int arrow = fxml.indexOf("fx:id=\"taskRouteArrowColumn\"");
        int dest = fxml.indexOf("fx:id=\"taskDestinationColumn\"");
        assertTrue(source >= 0 && arrow > source && dest > arrow);
        assertTrue(fxml.contains("prefWidth=\"250\""));
        assertTrue(fxml.contains("fx:id=\"taskRouteArrowColumn\" text=\"\""));
        assertTrue(fxml.contains("prefWidth=\"36\""));
    }


    @Test
    void routeArrowGraphicIsTextNotEllipsizedLabel() throws Exception {
        runOnFx(
                () -> {
                    javafx.scene.text.Text arrow =
                            WarehouseTaskVisualStyles.createRouteArrowGraphic();
                    assertEquals(WarehouseTaskVisualStyles.ROUTE_ARROW_TEXT, arrow.getText());
                    assertTrue(
                            arrow.getStyleClass()
                                    .contains(WarehouseTaskVisualStyles.ROUTE_ARROW));
                    assertFalse(arrow.getClass().getName().contains("Label"));

                    TableView<TaskRow> table = new TableView<>();
                    TableColumn<TaskRow, String> arrowCol = new TableColumn<>("");
                    arrowCol.setPrefWidth(36);
                    arrowCol.setMinWidth(36);
                    arrowCol.setMaxWidth(40);
                    arrowCol.setCellValueFactory(
                            c -> new javafx.beans.property.SimpleStringProperty(""));
                    arrowCol.setCellFactory(
                            column ->
                                    new TableCell<>() {
                                        private final javafx.scene.text.Text graphic =
                                                WarehouseTaskVisualStyles
                                                        .createRouteArrowGraphic();

                                        {
                                            setMouseTransparent(true);
                                            getStyleClass()
                                                    .add(
                                                            WarehouseTaskVisualStyles
                                                                    .ROUTE_ARROW_CELL);
                                            setAlignment(Pos.CENTER);
                                            setContentDisplay(
                                                    javafx.scene.control.ContentDisplay
                                                            .GRAPHIC_ONLY);
                                        }

                                        @Override
                                        protected void updateItem(String item, boolean empty) {
                                            super.updateItem(item, empty);
                                            setText(null);
                                            setGraphic(empty ? null : graphic);
                                        }
                                    });
                    table.getColumns().add(arrowCol);
                    table.setItems(
                            FXCollections.observableArrayList(
                                    new TaskRow(task(WarehouseTaskKind.TRANSFER_PREPARATION), null)));
                    Scene scene = new Scene(table, 200, 120);
                    Stage stage = new Stage();
                    stage.setScene(scene);
                    stage.show();
                    table.applyCss();
                    table.layout();

                    TableCell<?, ?> cell = null;
                    for (javafx.scene.Node node : table.lookupAll(".table-cell")) {
                        if (node instanceof TableCell<?, ?> candidate
                                && candidate.getTableColumn() == arrowCol
                                && candidate.getIndex() == 0) {
                            cell = candidate;
                            break;
                        }
                    }
                    assertNotNull(cell);
                    assertTrue(cell.isMouseTransparent());
                    assertEquals(Pos.CENTER, cell.getAlignment());
                    assertInstanceOf(javafx.scene.text.Text.class, cell.getGraphic());
                    assertEquals(
                            WarehouseTaskVisualStyles.ROUTE_ARROW_TEXT,
                            ((javafx.scene.text.Text) cell.getGraphic()).getText());
                    assertNull(cell.getText());

                    table.getItems().clear();
                    table.refresh();
                    table.applyCss();
                    table.layout();
                    TableCell<?, ?> emptyCell = null;
                    for (javafx.scene.Node node : table.lookupAll(".table-cell")) {
                        if (node instanceof TableCell<?, ?> candidate
                                && candidate.getTableColumn() == arrowCol) {
                            emptyCell = candidate;
                            break;
                        }
                    }
                    if (emptyCell != null) {
                        assertNull(emptyCell.getGraphic());
                        assertNull(emptyCell.getText());
                    }
                    stage.close();
                });
    }
    @Test
    void kindDotClassesRemainDistinctFromStatusDotsOnStyledNodes() throws Exception {
        runOnFx(
                () -> {
                    javafx.scene.shape.Circle kind = new javafx.scene.shape.Circle(4);
                    kind.getStyleClass()
                            .addAll(
                                    "tmp-wh-kind-dot",
                                    WarehouseTaskVisualStyles.KIND_DOT_RETURN);
                    javafx.scene.shape.Circle status = new javafx.scene.shape.Circle(6);
                    status.getStyleClass()
                            .addAll("tmp-status-dot", WarehouseTaskVisualStyles.STATUS_NEW);
                    assertTrue(kind.getStyleClass().contains(WarehouseTaskVisualStyles.KIND_DOT_RETURN));
                    assertFalse(kind.getStyleClass().contains("tmp-status-dot"));
                    assertFalse(status.getStyleClass().contains("tmp-wh-kind-dot"));
                });
    }

    private static WarehouseTaskView task(WarehouseTaskKind kind) {
        return new WarehouseTaskView(
                WarehouseTaskSource.TRANSFER_DOCUMENT,
                UUID.randomUUID(),
                null,
                "TR-X",
                "ORD",
                kind,
                WarehouseTaskState.NEW,
                SRC,
                "SRC",
                "Source",
                DST,
                "DST",
                "Dest",
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

    private static WarehouseTaskView supplyTask() {
        return new WarehouseTaskView(
                WarehouseTaskSource.WAREHOUSE_DEMAND,
                null,
                UUID.randomUUID(),
                "—",
                "ORD",
                WarehouseTaskKind.PRODUCTION_MATERIAL_SUPPLY,
                WarehouseTaskState.NEW,
                null,
                null,
                null,
                DST,
                "DST",
                "Dest",
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
}
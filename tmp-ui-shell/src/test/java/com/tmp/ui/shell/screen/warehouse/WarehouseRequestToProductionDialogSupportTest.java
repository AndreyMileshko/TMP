package com.tmp.ui.shell.screen.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.StockMoveLine;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.StockRow;
import com.tmp.warehouse.api.WarehouseApi.WarehouseStockCellLineView;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WarehouseRequestToProductionDialogSupportTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void createRequestDialogExposesRouteHeaderColumnsAndButtons() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        StockRow row =
                StockRow.from(
                        new WarehouseStockCellLineView(
                                warehouseId,
                                "MAIN",
                                "Main",
                                UUID.randomUUID(),
                                "A-01",
                                UUID.randomUUID(),
                                "ART-1",
                                "Pipe",
                                "red",
                                "6м",
                                "шт",
                                new BigDecimal("12.5")));

        AtomicReference<WarehouseRequestToProductionDialogSupport.RequestDialogSession> sessionRef =
                new AtomicReference<>();
        JavaFxTestSupport.runOnFxThread(
                () ->
                        sessionRef.set(
                                WarehouseRequestToProductionDialogSupport.createRequestDialog(
                                        List.of(row),
                                        "MAIN — Main",
                                        "PROD — Production")));

        WarehouseRequestToProductionDialogSupport.RequestDialogSession session = sessionRef.get();
        assertEquals(
                WarehouseRequestToProductionDialogSupport.TITLE, session.dialog().getTitle());
        assertEquals("MAIN — Main → PROD — Production", session.headerText());
        assertEquals(
                WarehouseRequestToProductionDialogSupport.COLUMN_HEADERS, session.columnHeaders());
        assertTrue(
                session.buttonLabels()
                        .contains(WarehouseRequestToProductionDialogSupport.CANCEL_BUTTON));
        assertTrue(
                session.buttonLabels()
                        .contains(WarehouseRequestToProductionDialogSupport.SUBMIT_BUTTON));
        List<StockMoveLine> lines = session.requireSubmission();
        assertEquals(1, lines.size());
        assertEquals(0, new BigDecimal("12.5").compareTo(lines.get(0).quantity()));
    }

    @Test
    void quantityValidationRejectsZeroNegativeAndExcess() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        WarehouseRequestToProductionDialogSupport.parseRequestQuantity(
                                "0", new BigDecimal("10")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        WarehouseRequestToProductionDialogSupport.parseRequestQuantity(
                                "-1", new BigDecimal("10")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        WarehouseRequestToProductionDialogSupport.parseRequestQuantity(
                                "11", new BigDecimal("10")));
        assertEquals(
                0,
                new BigDecimal("0.5")
                        .compareTo(
                                WarehouseRequestToProductionDialogSupport.parseRequestQuantity(
                                        "0,5", new BigDecimal("10"))));
    }

    @Test
    void aggregateByMaterialSumsExactBigDecimalQuantities() {
        UUID warehouseId = UUID.randomUUID();
        UUID material = UUID.randomUUID();
        UUID cellA = UUID.randomUUID();
        UUID cellB = UUID.randomUUID();
        StockRow rowA =
                StockRow.from(
                        new WarehouseStockCellLineView(
                                warehouseId,
                                "MAIN",
                                "Main",
                                cellA,
                                "A-01",
                                material,
                                "MAT-001",
                                "Profile",
                                "",
                                "",
                                "шт",
                                new BigDecimal("3")));
        StockRow rowB =
                StockRow.from(
                        new WarehouseStockCellLineView(
                                warehouseId,
                                "MAIN",
                                "Main",
                                cellB,
                                "B-01",
                                material,
                                "MAT-001",
                                "Profile",
                                "",
                                "",
                                "шт",
                                new BigDecimal("2")));
        List<StockMoveLine> aggregated =
                WarehouseRequestToProductionDialogSupport.aggregateByMaterial(
                        List.of(
                                StockMoveLine.from(rowA, new BigDecimal("3")),
                                StockMoveLine.from(rowB, new BigDecimal("2"))));
        assertEquals(1, aggregated.size());
        assertEquals(material, aggregated.get(0).materialReferenceId());
        assertEquals(0, new BigDecimal("5").compareTo(aggregated.get(0).quantity()));
    }

    @Test
    void textColumnsInstallTooltips() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        StockRow row =
                StockRow.from(
                        new WarehouseStockCellLineView(
                                warehouseId,
                                "MAIN",
                                "Main",
                                UUID.randomUUID(),
                                "A-01",
                                UUID.randomUUID(),
                                "ART-LONG-CODE",
                                "Very long material name for tooltip",
                                "color",
                                "size",
                                "шт",
                                new BigDecimal("1")));

        AtomicReference<WarehouseRequestToProductionDialogSupport.RequestDialogSession> sessionRef =
                new AtomicReference<>();
        JavaFxTestSupport.runOnFxThread(
                () ->
                        sessionRef.set(
                                WarehouseRequestToProductionDialogSupport.createRequestDialog(
                                        List.of(row), "MAIN — Main", "PROD — Production")));

        JavaFxTestSupport.runOnFxThread(
                () -> {
                    TableView<?> table = sessionRef.get().tableView();
                    table.applyCss();
                    table.layout();
                    @SuppressWarnings("unchecked")
                    TableColumn<Object, String> articleCol =
                            (TableColumn<Object, String>) table.getColumns().get(0);
                    TableCell<Object, String> cell = articleCol.getCellFactory().call(articleCol);
                    cell.updateTableView((TableView) table);
                    cell.updateTableColumn(articleCol);
                    cell.updateIndex(0);
                    assertEquals("ART-LONG-CODE", cell.getText());
                    Tooltip tip = cell.getTooltip();
                    assertTrue(tip != null && "ART-LONG-CODE".equals(tip.getText()));
                });
    }

    @Test
    void invalidQuantityKeepsDialogOpenMarksFieldPreservesValuesAndBlocksSubmit()
            throws Exception {
        UUID warehouseId = UUID.randomUUID();
        UUID materialA = UUID.randomUUID();
        UUID materialB = UUID.randomUUID();
        StockRow rowA =
                StockRow.from(
                        new WarehouseStockCellLineView(
                                warehouseId,
                                "MAIN",
                                "Main",
                                UUID.randomUUID(),
                                "A-01",
                                materialA,
                                "A1",
                                "Mat A",
                                "",
                                "",
                                "шт",
                                new BigDecimal("10")));
        StockRow rowB =
                StockRow.from(
                        new WarehouseStockCellLineView(
                                warehouseId,
                                "MAIN",
                                "Main",
                                UUID.randomUUID(),
                                "B-01",
                                materialB,
                                "B1",
                                "Mat B",
                                "",
                                "",
                                "шт",
                                new BigDecimal("5")));

        AtomicInteger submitCalls = new AtomicInteger();
        JavaFxTestSupport.runOnFxThread(
                () -> {
                    WarehouseRequestToProductionDialogSupport.RequestDialogSession session =
                            WarehouseRequestToProductionDialogSupport.createRequestDialog(
                                    List.of(rowA, rowB), "MAIN — Main", "PROD — Production");
                    forceLayout(session);
                    showDialogForSubmitFilter(session);
                    session.setOnValidSubmit(lines -> submitCalls.incrementAndGet());

                    session.rows().get(0).quantityTextProperty().set("11");
                    session.rows().get(1).quantityTextProperty().set("3");
                    forceLayout(session);

                    Button submit =
                            (Button)
                                    session.dialog()
                                            .getDialogPane()
                                            .lookupButton(session.submitType());
                    submit.fireEvent(new ActionEvent());

                    assertTrue(session.dialog().isShowing());
                    assertTrue(session.isErrorVisible());
                    assertTrue(session.errorText().contains("доступный остаток"));
                    assertTrue(session.rows().get(0).isQuantityInvalid());
                    assertFalse(session.rows().get(1).isQuantityInvalid());
                    assertEquals("11", session.rows().get(0).quantityTextProperty().get());
                    assertEquals("3", session.rows().get(1).quantityTextProperty().get());
                    assertEquals(0, submitCalls.get());

                    session.dialog().close();
                });
    }

    @Test
    void correctedQuantityAllowsSubmitAndClearsFieldInvalidState() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        StockRow row =
                StockRow.from(
                        new WarehouseStockCellLineView(
                                warehouseId,
                                "MAIN",
                                "Main",
                                UUID.randomUUID(),
                                "A-01",
                                UUID.randomUUID(),
                                "A1",
                                "Mat A",
                                "",
                                "",
                                "шт",
                                new BigDecimal("10")));

        AtomicReference<List<StockMoveLine>> submitted = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThread(
                () -> {
                    WarehouseRequestToProductionDialogSupport.RequestDialogSession session =
                            WarehouseRequestToProductionDialogSupport.createRequestDialog(
                                    List.of(row), "MAIN — Main", "PROD — Production");
                    forceLayout(session);
                    showDialogForSubmitFilter(session);
                    session.setOnValidSubmit(submitted::set);

                    session.rows().get(0).quantityTextProperty().set("99");
                    Button submit =
                            (Button)
                                    session.dialog()
                                            .getDialogPane()
                                            .lookupButton(session.submitType());
                    submit.fireEvent(new ActionEvent());
                    assertTrue(session.isErrorVisible());
                    assertTrue(session.rows().get(0).isQuantityInvalid());
                    assertTrue(session.dialog().isShowing());

                    session.rows().get(0).quantityTextProperty().set("4");
                    assertFalse(session.rows().get(0).isQuantityInvalid());
                    session.clearError();
                    submit.fireEvent(new ActionEvent());

                    assertFalse(session.isErrorVisible());
                    assertEquals(1, submitted.get().size());
                    assertEquals(
                            0, new BigDecimal("4").compareTo(submitted.get().get(0).quantity()));
                    assertTrue(session.dialog().isShowing());

                    session.dialog().close();
                });
    }

    @Test
    void backendFailureCallbackKeepsDialogEditableWithPreservedQuantities() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        StockRow row =
                StockRow.from(
                        new WarehouseStockCellLineView(
                                warehouseId,
                                "MAIN",
                                "Main",
                                UUID.randomUUID(),
                                "A-01",
                                UUID.randomUUID(),
                                "A1",
                                "Mat A",
                                "",
                                "",
                                "шт",
                                new BigDecimal("10")));

        JavaFxTestSupport.runOnFxThread(
                () -> {
                    WarehouseRequestToProductionDialogSupport.RequestDialogSession session =
                            WarehouseRequestToProductionDialogSupport.createRequestDialog(
                                    List.of(row), "MAIN — Main", "PROD — Production");
                    forceLayout(session);
                    showDialogForSubmitFilter(session);
                    session.rows().get(0).quantityTextProperty().set("7");
                    session.setOnValidSubmit(
                            lines ->
                                    session.showError(
                                            "Недостаточно прав для складской операции."));

                    Button submit =
                            (Button)
                                    session.dialog()
                                            .getDialogPane()
                                            .lookupButton(session.submitType());
                    submit.fireEvent(new ActionEvent());

                    assertTrue(session.dialog().isShowing());
                    assertTrue(session.isErrorVisible());
                    assertEquals(
                            "Недостаточно прав для складской операции.", session.errorText());
                    assertEquals("7", session.rows().get(0).quantityTextProperty().get());
                    assertFalse(session.rows().get(0).isQuantityInvalid());

                    session.dialog().close();
                });
    }

    @Test
    void nonNumericAndInvalidDecimalKeepDialogOpenWithoutSubmit() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        StockRow row =
                StockRow.from(
                        new WarehouseStockCellLineView(
                                warehouseId,
                                "MAIN",
                                "Main",
                                UUID.randomUUID(),
                                "A-01",
                                UUID.randomUUID(),
                                "A1",
                                "Mat A",
                                "",
                                "",
                                "шт",
                                new BigDecimal("10")));

        AtomicBoolean submitted = new AtomicBoolean();
        JavaFxTestSupport.runOnFxThread(
                () -> {
                    WarehouseRequestToProductionDialogSupport.RequestDialogSession session =
                            WarehouseRequestToProductionDialogSupport.createRequestDialog(
                                    List.of(row), "MAIN — Main", "PROD — Production");
                    forceLayout(session);
                    showDialogForSubmitFilter(session);
                    session.setOnValidSubmit(lines -> submitted.set(true));

                    session.rows().get(0).quantityTextProperty().set("abc");
                    Button submit =
                            (Button)
                                    session.dialog()
                                            .getDialogPane()
                                            .lookupButton(session.submitType());
                    submit.fireEvent(new ActionEvent());
                    assertTrue(session.dialog().isShowing());
                    assertTrue(session.isErrorVisible());
                    assertTrue(session.rows().get(0).isQuantityInvalid());
                    assertFalse(submitted.get());

                    session.rows().get(0).quantityTextProperty().set("1.2.3");
                    submit.fireEvent(new ActionEvent());
                    assertTrue(session.dialog().isShowing());
                    assertFalse(submitted.get());

                    session.dialog().close();
                });
    }

    private static void showDialogForSubmitFilter(
            WarehouseRequestToProductionDialogSupport.RequestDialogSession session) {
        javafx.stage.Stage owner = new javafx.stage.Stage();
        owner.setScene(new javafx.scene.Scene(new Region(), 40, 40));
        owner.show();
        session.dialog().initOwner(owner);
        session.dialog().show();
        forceLayout(session);
    }

    private static void forceLayout(
            WarehouseRequestToProductionDialogSupport.RequestDialogSession session) {
        Region pane = session.dialog().getDialogPane();
        pane.applyCss();
        pane.layout();
        TableView<?> table = session.tableView();
        table.applyCss();
        table.layout();
        table.setPrefWidth(980);
        table.setPrefHeight(280);
        table.resize(980, 280);
        table.layout();
    }
}
package com.tmp.ui.shell.screen.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentPrintLineView;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentPrintView;
import com.tmp.warehouse.api.WarehouseApi.TransferPrintAllocationKind;
import com.tmp.warehouse.api.WarehouseApi.TransferPrintDocumentKind;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WarehouseTransferPrintSupportTest {

    @BeforeAll
    static void startToolkit() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void columnHeadersMatchStockOrderExactly() {
        assertEquals(
                List.of(
                        "Ячейка",
                        "Артикул",
                        "Наименование",
                        "Цвет",
                        "Размер",
                        "Количество",
                        "Ед."),
                WarehouseTransferPrintSupport.COLUMN_HEADERS);
        assertEquals(7, WarehouseTransferPrintSupport.COLUMN_HEADERS.size());
    }

    @Test
    void stylesheetDefinesFullHorizontalAndVerticalGrid() throws Exception {
        Path css =
                Path.of(
                        "src/main/resources/com/tmp/ui/shell/screen/warehouse/warehouse-print.css");
        String text = Files.readString(css, StandardCharsets.UTF_8);
        assertTrue(text.contains(".tmp-warehouse-print-table"));
        assertTrue(text.contains("-fx-border-color: black"));
        assertTrue(text.contains(".tmp-warehouse-print-table-header"));
        assertTrue(text.contains(".tmp-warehouse-print-cell"));
        assertTrue(text.contains("-fx-border-width: 0 0.5 0.5 0"));
        assertFalse(text.contains("#2F9E55"));
        assertFalse(text.contains("#278A49"));
    }

    @Test
    void landscapeColumnWeightsPrioritizeArticleAndName() {
        double[] weights = WarehouseTransferPrintSupport.COLUMN_WIDTH_WEIGHTS;
        assertEquals(7, weights.length);
        double sum = 0;
        for (double weight : weights) {
            sum += weight;
        }
        assertEquals(1.0, sum, 0.0001);
        assertTrue(weights[1] > weights[0], "Артикул wider than Ячейка");
        assertTrue(weights[2] > weights[1], "Наименование widest content column");
        assertTrue(weights[4] >= 0.10, "Размер must remain fully readable");
        assertTrue(weights[5] >= 0.12, "Количество must remain fully readable");
    }

    @Test
    void buildTableAppliesFullGridStylesAndSevenColumns() throws Exception {
        JavaFxTestSupport.runOnFxThread(
                () -> {
                    TransferDocumentPrintLineView line =
                            new TransferDocumentPrintLineView(
                                    1,
                                    UUID.randomUUID(),
                                    UUID.randomUUID(),
                                    "ART-1",
                                    "Очень длинное наименование материала для переноса строк",
                                    "Белый",
                                    "6000",
                                    "м",
                                    new BigDecimal("3"),
                                    UUID.randomUUID(),
                                    "A-01",
                                    TransferPrintAllocationKind.PLANNED);
                    GridPane table =
                            WarehouseTransferPrintSupport.buildTable(List.of(line), 760);
                    assertEquals(
                            WarehouseTransferPrintSupport.PRINT_TABLE_STYLE,
                            table.getStyleClass().get(0));
                    assertEquals(7, table.getColumnConstraints().size());
                    Label header0 = (Label) getNode(table, 0, 0);
                    assertTrue(
                            header0.getStyleClass()
                                    .contains(
                                            WarehouseTransferPrintSupport
                                                    .PRINT_TABLE_HEADER_STYLE));
                    assertEquals("Ячейка", ((Label) getNode(table, 0, 0)).getText());
                    assertEquals("Артикул", ((Label) getNode(table, 1, 0)).getText());
                    assertEquals("Наименование", ((Label) getNode(table, 2, 0)).getText());
                    assertEquals("Цвет", ((Label) getNode(table, 3, 0)).getText());
                    assertEquals("Размер", ((Label) getNode(table, 4, 0)).getText());
                    Label qtyHeader = (Label) getNode(table, 5, 0);
                    assertEquals("Количество", qtyHeader.getText());
                    assertFalse(qtyHeader.isWrapText());
                    assertEquals("Ед.", ((Label) getNode(table, 6, 0)).getText());
                    for (int c = 0; c < 7; c++) {
                        Label header = (Label) getNode(table, c, 0);
                        assertFalse(header.isWrapText(), "header wrap at " + c);
                        assertFalse(header.getText().contains("…"));
                        assertFalse(header.getText().contains("..."));
                    }
                    assertEquals(
                            WarehouseTransferPrintSupport.COLUMN_WIDTH_WEIGHTS[2] * 100.0,
                            table.getColumnConstraints().get(2).getPercentWidth(),
                            0.0001);
                    Label cellName = (Label) getNode(table, 2, 1);
                    assertTrue(
                            cellName.getStyleClass()
                                    .contains(WarehouseTransferPrintSupport.PRINT_CELL_STYLE));
                    assertTrue(cellName.isWrapText());
                    Label qty = (Label) getNode(table, 5, 1);
                    assertTrue(qty.getText().replace('\u00a0', ' ').contains("3"));
                });
    }

    @Test
    void buildPageRepeatsHeaderAndSignaturesOnAdditionalPages() throws Exception {
        JavaFxTestSupport.runOnFxThread(
                () -> {
                    TransferDocumentPrintView document =
                            new TransferDocumentPrintView(
                                    UUID.randomUUID(),
                                    "TR-9",
                                    Instant.parse("2026-09-11T10:00:00Z"),
                                    "DRAFT",
                                    "Перемещение",
                                    TransferPrintDocumentKind.TRANSFER_PICKING,
                                    WarehouseTransferPrintSupport.COMPANY_NAME.equals("ТОП СТИЛЬ")
                                            ? "ЗАДАНИЕ НА КОМПЛЕКТАЦИЮ МАТЕРИАЛОВ"
                                            : "ЗАДАНИЕ НА КОМПЛЕКТАЦИЮ МАТЕРИАЛОВ",
                                    List.of("100"),
                                    UUID.randomUUID(),
                                    "SRC",
                                    "Source",
                                    UUID.randomUUID(),
                                    "DST",
                                    "Destination",
                                    TransferPrintAllocationKind.PLANNED,
                                    true,
                                    null,
                                    List.of(
                                            new TransferDocumentPrintLineView(
                                                    1,
                                                    UUID.randomUUID(),
                                                    UUID.randomUUID(),
                                                    "A",
                                                    "Name",
                                                    "C",
                                                    "S",
                                                    "шт",
                                                    BigDecimal.ONE,
                                                    null,
                                                    "A-01",
                                                    TransferPrintAllocationKind.PLANNED)));
                    Node page =
                            WarehouseTransferPrintSupport.buildPage(
                                    document, document.lines(), 2, 3, null);
                    String text = flattenText(page);
                    assertTrue(text.contains("ТОП СТИЛЬ"));
                    assertTrue(text.contains("ЗАДАНИЕ НА КОМПЛЕКТАЦИЮ МАТЕРИАЛОВ"));
                    assertTrue(text.contains("Передал:"));
                    assertTrue(text.contains("Принял:"));
                    assertFalse(text.contains("Собрал"));
                    assertTrue(text.contains("Стр. 2 / 3"));
                    assertTrue(text.contains("Ячейка"));
                    assertTrue(text.contains("Количество"));
                    assertTrue(text.contains("Дата документа:"));
                    assertFalse(text.contains("Дата операции:"));
                });
    }

    @Test
    void historicalReprintShowsDocumentAndOperationDates() throws Exception {
        JavaFxTestSupport.runOnFxThread(
                () -> {
                    Instant created = Instant.parse("2026-06-08T08:00:00Z");
                    Instant operated = Instant.parse("2026-06-10T14:30:00Z");
                    TransferDocumentPrintView document =
                            new TransferDocumentPrintView(
                                            UUID.randomUUID(),
                                            "TR-HIST",
                                            created,
                                            "SENT",
                                            "Перемещение",
                                            TransferPrintDocumentKind.TRANSFER_PICKING,
                                            "ЗАДАНИЕ НА КОМПЛЕКТАЦИЮ МАТЕРИАЛОВ",
                                            List.of("26106075"),
                                            UUID.randomUUID(),
                                            "SRC",
                                            "Source",
                                            UUID.randomUUID(),
                                            "DST",
                                            "Destination",
                                            TransferPrintAllocationKind.CONFIRMED,
                                            false,
                                            null,
                                            List.of(
                                                    new TransferDocumentPrintLineView(
                                                            1,
                                                            UUID.randomUUID(),
                                                            UUID.randomUUID(),
                                                            "A",
                                                            "Name",
                                                            "C",
                                                            "S",
                                                            "шт",
                                                            BigDecimal.ONE,
                                                            null,
                                                            "A-01",
                                                            TransferPrintAllocationKind
                                                                    .CONFIRMED)))
                                    .withOperationOccurredAt(operated);
                    Node page =
                            WarehouseTransferPrintSupport.buildPage(
                                    document, document.lines(), 1, 1, null);
                    String text = flattenText(page);
                    assertTrue(text.contains("Дата документа:"));
                    assertTrue(text.contains("Дата операции:"));
                    assertFalse(text.contains("Ячейки отбора — плановые"));
                });
    }

    private static Node getNode(GridPane table, int column, int row) {
        for (Node child : table.getChildren()) {
            Integer c = GridPane.getColumnIndex(child);
            Integer r = GridPane.getRowIndex(child);
            if (c != null && r != null && c == column && r == row) {
                return child;
            }
        }
        throw new AssertionError("Missing cell " + column + "," + row);
    }

    private static String flattenText(Node node) {
        StringBuilder sb = new StringBuilder();
        collect(node, sb);
        return sb.toString();
    }

    private static void collect(Node node, StringBuilder sb) {
        if (node instanceof Label label) {
            sb.append(label.getText()).append('\n');
        }
        if (node instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collect(child, sb);
            }
        }
    }
}
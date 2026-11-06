package com.tmp.ui.shell.screen.warehouse;

import com.tmp.ui.shell.order.DecimalUiFormat;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentPrintLineView;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentPrintView;
import com.tmp.warehouse.api.WarehouseApi.TransferPrintAllocationKind;
import com.tmp.warehouse.api.WarehouseApi.TransferPrintDocumentKind;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.print.PageLayout;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * A4 landscape black-and-white JavaFX print renderer for approved Warehouse documents (picking / return).
 * Read-only; always opens the native {@link PrinterJob#showPrintDialog(Window)} before printing.
 * Uses style classes only (no inline {@code setStyle}).
 */
public final class WarehouseTransferPrintSupport {

    public static final String COMPANY_NAME = "ТОП СТИЛЬ";
    public static final String PRINT_ROOT_STYLE = "tmp-warehouse-print-root";
    public static final String PRINT_COMPANY_STYLE = "tmp-warehouse-print-company";
    public static final String PRINT_HEADING_STYLE = "tmp-warehouse-print-heading";
    public static final String PRINT_META_STYLE = "tmp-warehouse-print-meta";
    public static final String PRINT_NOTE_STYLE = "tmp-warehouse-print-note";
    public static final String PRINT_TABLE_STYLE = "tmp-warehouse-print-table";
    public static final String PRINT_TABLE_HEADER_STYLE = "tmp-warehouse-print-table-header";
    public static final String PRINT_CELL_STYLE = "tmp-warehouse-print-cell";
    public static final String PRINT_CELL_FIRST_COL_STYLE = "tmp-warehouse-print-cell-first-col";
    public static final String PRINT_FOOTER_STYLE = "tmp-warehouse-print-footer";
    public static final String PRINT_SIGNATURE_STYLE = "tmp-warehouse-print-signature";

    /** Exact seven Stock-aligned print columns. */
    public static final List<String> COLUMN_HEADERS = TransferDocumentPrintLineView.COLUMN_HEADERS;

    /**
     * Landscape column width weights (Ячейка … Ед.). Extra width goes to Артикул /
     * Наименование; Размер and Количество stay fully readable without mid-word wrap.
     */
    static final double[] COLUMN_WIDTH_WEIGHTS = {
        0.10, 0.16, 0.32, 0.12, 0.10, 0.12, 0.08
    };

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withLocale(Locale.forLanguageTag("ru"));

    private WarehouseTransferPrintSupport() {}

    /**
     * Production entry: native printer dialog must be confirmed before any
     * {@link PrinterJob#printPage}. Cancellation never prints.
     */
    public static void print(
            TransferDocumentPrintView document, Window owner, Consumer<String> errorConsumer) {
        print(document, owner, errorConsumer, PrintJobOperations.system());
    }

    /**
     * Testable entry using an explicit {@link PrintJobOperations} bridge (dialog / pages / end).
     */
    static void print(
            TransferDocumentPrintView document,
            Window owner,
            Consumer<String> errorConsumer,
            PrintJobOperations operations) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(errorConsumer, "errorConsumer");
        Objects.requireNonNull(operations, "operations");
        if (!operations.hasAvailablePrinters()) {
            errorConsumer.accept(
                    "Нет доступных принтеров. Подключите принтер и повторите печать.");
            return;
        }
        if (operations.isUiUnavailable()) {
            // JavaFX showPrintDialog() returns true without UI when headless — refuse silent print.
            errorConsumer.accept(
                    "Диалог печати недоступен в текущем окружении. Печать отменена.");
            return;
        }
        PrinterJob job = operations.createJob();
        // Landscape must be set before the native dialog so physical printers open landscape.
        operations.configureA4Landscape(job);
        Window dialogOwner = resolvePrintOwner(owner);
        boolean proceeded = operations.showNativePrintDialog(job, dialogOwner);
        if (!proceeded) {
            operations.cancel(job);
            return;
        }
        PageLayout pageLayout;
        try {
            // Re-create + verify A4 landscape for the printer actually selected after the dialog.
            pageLayout = operations.requireA4LandscapeLayout(job);
        } catch (RuntimeException ex) {
            operations.cancel(job);
            errorConsumer.accept(
                    ex.getMessage() == null || ex.getMessage().isBlank()
                            ? "Выбранный принтер не поддерживает A4 LANDSCAPE."
                            : ex.getMessage());
            return;
        }
        if (!operations.isVerifiedLandscape(pageLayout)) {
            operations.cancel(job);
            errorConsumer.accept(
                    "Эффективный PageLayout не A4 LANDSCAPE — печать портретом запрещена.");
            return;
        }
        Printer selectedPrinter = job == null ? null : job.getPrinter();
        if (operations.isPdfOutputPrinter(selectedPrinter)) {
            // Virtual PDF drivers (esp. Microsoft Print to PDF) ignore LANDSCAPE and emit a
            // portrait MediaBox. Export a real A4 landscape PDF after the native dialog instead.
            String suggested =
                    sanitizeFileName(document.documentNumber())
                            + "-"
                            + (document.documentKind()
                                            == TransferPrintDocumentKind.MATERIAL_RETURN
                                    ? "Возврат"
                                    : "Передача")
                            + ".pdf";
            File target = operations.choosePdfOutputFile(dialogOwner, suggested);
            if (target == null) {
                operations.cancel(job);
                return;
            }
            try {
                WarehouseTransferLandscapePdfExport.export(
                        document, pageLayout, target.toPath());
                operations.cancel(job);
            } catch (IOException | RuntimeException ex) {
                operations.cancel(job);
                errorConsumer.accept(
                        "Ошибка сохранения PDF: "
                                + (ex.getMessage() == null || ex.getMessage().isBlank()
                                        ? ex.getClass().getSimpleName()
                                        : ex.getMessage()));
            }
            return;
        }
        List<List<TransferDocumentPrintLineView>> pages =
                WarehouseTransferPrintPagination.paginate(document);
        int totalPages = pages.size();
        try {
            for (int i = 0; i < totalPages; i++) {
                // Same verified landscape layout for every printed page — never a stale portrait.
                Node pageNode = buildPage(document, pages.get(i), i + 1, totalPages, pageLayout);
                boolean ok = operations.printPage(job, pageLayout, pageNode);
                if (!ok) {
                    errorConsumer.accept("Печать прервана на странице " + (i + 1) + ".");
                    operations.cancel(job);
                    return;
                }
            }
            operations.end(job);
        } catch (RuntimeException ex) {
            try {
                operations.cancel(job);
            } catch (RuntimeException ignored) {
                // best-effort cleanup
            }
            errorConsumer.accept(
                    "Ошибка печати: "
                            + (ex.getMessage() == null || ex.getMessage().isBlank()
                                    ? ex.getClass().getSimpleName()
                                    : ex.getMessage()));
        }
    }

    static String sanitizeFileName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "document";
        }
        return raw.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    /**
     * Prefer a visible non-modal Stage (main shell). Modal JavaFX Dialog windows used as native
     * DialogOwner on Windows frequently hide or skip the standard printer dialog.
     */
    static Window resolvePrintOwner(Window preferred) {
        Window current = preferred;
        Window fallback = preferred;
        while (current != null) {
            fallback = current;
            if (current instanceof Stage stage
                    && stage.isShowing()
                    && stage.getModality() == javafx.stage.Modality.NONE) {
                return stage;
            }
            current = current instanceof Stage stage ? stage.getOwner() : null;
        }
        return fallback;
    }

    public static void showPrintError(Window owner, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle("Печать");
        alert.setHeaderText("Не удалось напечатать документ");
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.showAndWait();
    }

    static Node buildPage(
            TransferDocumentPrintView document,
            List<TransferDocumentPrintLineView> pageLines,
            int pageIndex,
            int pageCount,
            PageLayout pageLayout) {
        VBox root = new VBox(6);
        root.getStyleClass().add(PRINT_ROOT_STYLE);
        double printableWidth =
                pageLayout == null
                        ? 760
                        : Math.max(420, pageLayout.getPrintableWidth() - 4);
        root.setPrefWidth(printableWidth);
        root.setMaxWidth(printableWidth);

        Label company = new Label(COMPANY_NAME);
        company.getStyleClass().add(PRINT_COMPANY_STYLE);
        company.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));

        Label heading = new Label(document.printHeading());
        heading.getStyleClass().add(PRINT_HEADING_STYLE);
        heading.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        heading.setWrapText(true);

        VBox meta = new VBox(1);
        meta.getChildren().add(metaLabel("Документ: " + document.documentNumber()));
        meta.getChildren().add(metaLabel("Дата документа: " + formatDocumentDate(document)));
        if (document.operationOccurredAt() != null) {
            meta.getChildren()
                    .add(
                            metaLabel(
                                    "Дата операции: "
                                            + formatInstant(document.operationOccurredAt())));
        }
        meta.getChildren()
                .addAll(
                        metaLabel("Заказ: " + formatOrders(document)),
                        metaLabel(
                                "Откуда: "
                                        + formatWarehouse(
                                                document.sourceWarehouseCode(),
                                                document.sourceWarehouseName())),
                        metaLabel(
                                "Куда: "
                                        + formatWarehouse(
                                                document.destinationWarehouseCode(),
                                                document.destinationWarehouseName())));

        root.getChildren().addAll(company, heading, meta);

        if (document.advisoryNote() != null) {
            Label note = new Label(document.advisoryNote());
            note.getStyleClass().add(PRINT_NOTE_STYLE);
            note.setWrapText(true);
            note.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 9));
            root.getChildren().add(note);
        } else if (document.plannedAllocations()
                || document.allocationKind() == TransferPrintAllocationKind.PLANNED) {
            String plannedText =
                    document.documentKind() == TransferPrintDocumentKind.MATERIAL_RETURN
                            ? "Ячейки возврата — плановые (возврат ещё не выполнен)."
                            : "Ячейки отбора — плановые (документ ещё не передан).";
            Label planned = new Label(plannedText);
            planned.getStyleClass().add(PRINT_NOTE_STYLE);
            planned.setWrapText(true);
            planned.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 9));
            root.getChildren().add(planned);
        }

        root.getChildren().add(buildTable(pageLines, printableWidth));

        HBox footer = new HBox(28);
        footer.getStyleClass().add(PRINT_FOOTER_STYLE);
        Label issued = new Label("Передал: ____________________");
        issued.getStyleClass().add(PRINT_SIGNATURE_STYLE);
        Label received = new Label("Принял: ____________________");
        received.getStyleClass().add(PRINT_SIGNATURE_STYLE);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label pageLabel = new Label("Стр. " + pageIndex + " / " + pageCount);
        pageLabel.getStyleClass().add(PRINT_META_STYLE);
        footer.getChildren().addAll(issued, received, spacer, pageLabel);
        root.getChildren().add(footer);

        BorderPane pageRoot = new BorderPane(root);
        pageRoot.getStyleClass().add(PRINT_ROOT_STYLE);
        stylesheetUrl().ifPresent(url -> pageRoot.getStylesheets().add(url));
        return pageRoot;
    }

    static GridPane buildTable(
            List<TransferDocumentPrintLineView> lines, double printableWidth) {
        GridPane table = new GridPane();
        table.getStyleClass().add(PRINT_TABLE_STYLE);
        table.setHgap(0);
        table.setVgap(0);
        for (double weight : COLUMN_WIDTH_WEIGHTS) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(weight * 100.0);
            cc.setHgrow(Priority.ALWAYS);
            table.getColumnConstraints().add(cc);
        }
        for (int c = 0; c < COLUMN_HEADERS.size(); c++) {
            Label header = new Label(COLUMN_HEADERS.get(c));
            header.getStyleClass().add(PRINT_TABLE_HEADER_STYLE);
            if (c == 0) {
                header.getStyleClass().add(PRINT_CELL_FIRST_COL_STYLE);
            }
            header.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
            header.setMaxWidth(Double.MAX_VALUE);
            header.setMaxHeight(Double.MAX_VALUE);
            header.setTextAlignment(TextAlignment.CENTER);
            header.setWrapText(false); // one-line headers so Количество fits
            GridPane.setFillWidth(header, true);
            GridPane.setFillHeight(header, true);
            table.add(header, c, 0);
        }
        int row = 1;
        for (TransferDocumentPrintLineView line : lines) {
            addCell(table, 0, row, line.storageCellCode(), TextAlignment.LEFT, true);
            addCell(table, 1, row, line.article(), TextAlignment.LEFT, false);
            addCell(table, 2, row, line.materialName(), TextAlignment.LEFT, false);
            addCell(table, 3, row, blankDash(line.color()), TextAlignment.LEFT, false);
            addCell(table, 4, row, blankDash(line.size()), TextAlignment.CENTER, false);
            addCell(
                    table,
                    5,
                    row,
                    DecimalUiFormat.formatRu(line.quantity()),
                    TextAlignment.RIGHT,
                    false);
            addCell(table, 6, row, blankDash(line.unitOfMeasure()), TextAlignment.CENTER, false);
            row++;
        }
        table.setMaxWidth(printableWidth);
        return table;
    }

    private static void addCell(
            GridPane table,
            int column,
            int row,
            String text,
            TextAlignment align,
            boolean firstColumn) {
        Label label = new Label(text == null || text.isBlank() ? "—" : text);
        label.getStyleClass().add(PRINT_CELL_STYLE);
        if (firstColumn) {
            label.getStyleClass().add(PRINT_CELL_FIRST_COL_STYLE);
        }
        label.setWrapText(true);
        label.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 10));
        label.setTextAlignment(align);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setMaxHeight(Double.MAX_VALUE);
        GridPane.setFillWidth(label, true);
        GridPane.setFillHeight(label, true);
        table.add(label, column, row);
    }

    private static Label metaLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add(PRINT_META_STYLE);
        label.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 10));
        label.setWrapText(true);
        return label;
    }

    private static String blankDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private static String formatDocumentDate(TransferDocumentPrintView document) {
        return formatInstant(document.documentCreatedAt());
    }

    private static String formatInstant(Instant instant) {
        return DATE_FORMAT.format(instant.atZone(ZoneId.systemDefault()).toLocalDateTime());
    }

    private static String formatOrders(TransferDocumentPrintView document) {
        if (document.orderNumbers() == null || document.orderNumbers().isEmpty()) {
            return "—";
        }
        return String.join(", ", document.orderNumbers());
    }

    private static String formatWarehouse(String code, String name) {
        if (code == null || code.isBlank()) {
            return name == null || name.isBlank() ? "—" : name;
        }
        if (name == null || name.isBlank()) {
            return code;
        }
        return code + " — " + name;
    }

    public static Optional<String> stylesheetUrl() {
        var resource =
                WarehouseTransferPrintSupport.class
                        .getClassLoader()
                        .getResource("com/tmp/ui/shell/screen/warehouse/warehouse-print.css");
        return resource == null ? Optional.empty() : Optional.of(resource.toExternalForm());
    }
}
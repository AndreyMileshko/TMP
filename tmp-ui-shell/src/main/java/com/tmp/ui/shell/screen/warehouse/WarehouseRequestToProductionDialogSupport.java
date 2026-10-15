package com.tmp.ui.shell.screen.warehouse;

import com.tmp.ui.shell.order.DecimalQuantityParser;
import com.tmp.ui.shell.order.DecimalUiFormat;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.StockMoveLine;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.StockRow;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Compact dialog for Остатки → «Запросить на производство». Creates a Transfer Document DRAFT only
 * (caller must not send). Source cell is informational; quantities are editable per selected stock
 * row and aggregated by material when submitting.
 */
public final class WarehouseRequestToProductionDialogSupport {

    public static final String TITLE = "Запросить на производство";
    public static final String CANCEL_BUTTON = "Отмена";
    public static final String SUBMIT_BUTTON = "Запросить";

    public static final List<String> COLUMN_HEADERS =
            List.of(
                    "Артикул",
                    "Наименование",
                    "Цвет",
                    "Размер",
                    "Ячейка",
                    "Доступно",
                    "Запросить",
                    "Ед.");

    public static final double DIALOG_PREF_WIDTH = 980;
    public static final double DIALOG_MIN_WIDTH = 760;
    public static final double DIALOG_PREF_HEIGHT = 520;
    public static final double DIALOG_MIN_HEIGHT = 380;

    private static final double COL_ARTICLE_MIN = 110;
    private static final double COL_ARTICLE_PREF = 140;
    private static final double COL_NAME_MIN = 160;
    private static final double COL_NAME_PREF = 240;
    private static final double COL_COLOR_MIN = 90;
    private static final double COL_COLOR_PREF = 120;
    private static final double COL_SIZE_MIN = 70;
    private static final double COL_SIZE_PREF = 90;
    private static final double COL_CELL_MIN = 80;
    private static final double COL_CELL_PREF = 100;
    private static final double COL_AVAILABLE_MIN = 90;
    private static final double COL_AVAILABLE_PREF = 105;
    private static final double COL_QTY_MIN = 110;
    private static final double COL_QTY_PREF = 130;
    private static final double COL_UNIT_MIN = 50;
    private static final double COL_UNIT_PREF = 60;
    private static final double TABLE_SIDE_INSETS = 28;

    public static final String INPUT_ERROR_STYLE = "tmp-input-error";
    public static final String MESSAGE_ERROR_STYLE = "tmp-message-error";

    private WarehouseRequestToProductionDialogSupport() {}

    public static String routeHeader(String sourceWarehouseLabel, String productionWarehouseLabel) {
        return displayOrDash(sourceWarehouseLabel) + " → " + displayOrDash(productionWarehouseLabel);
    }

    /**
     * Builds the request dialog. Does not show it — caller invokes {@link Dialog#showAndWait()}.
     */
    public static RequestDialogSession createRequestDialog(
            List<StockRow> selected,
            String sourceWarehouseLabel,
            String productionWarehouseLabel) {
        Objects.requireNonNull(selected, "selected");
        Objects.requireNonNull(sourceWarehouseLabel, "sourceWarehouseLabel");
        Objects.requireNonNull(productionWarehouseLabel, "productionWarehouseLabel");
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("selected must not be empty");
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(TITLE);
        dialog.setHeaderText(routeHeader(sourceWarehouseLabel, productionWarehouseLabel));
        dialog.setResizable(true);
        ButtonType submitType = new ButtonType(SUBMIT_BUTTON, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType(CANCEL_BUTTON, ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(cancelType, submitType);
        dialog.getDialogPane().getStyleClass().add("tmp-dialog");
        dialog.getDialogPane().setPrefSize(DIALOG_PREF_WIDTH, DIALOG_PREF_HEIGHT);
        dialog.getDialogPane().setMinSize(DIALOG_MIN_WIDTH, DIALOG_MIN_HEIGHT);
        dialog.getDialogPane().setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        Label destinationLabel =
                new Label("Склад назначения: " + displayOrDash(productionWarehouseLabel));
        destinationLabel.setMaxWidth(Double.MAX_VALUE);

        List<RequestDialogRow> requestRows = new ArrayList<>();
        for (StockRow row : selected) {
            requestRows.add(new RequestDialogRow(row));
        }
        Label totalsLabel = new Label("Итого: " + formatRequestQuantityTotalsByUnit(requestRows));
        totalsLabel.setMaxWidth(Double.MAX_VALUE);

        TableView<RequestDialogRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(280);
        table.setMinHeight(160);
        table.setMaxWidth(Double.MAX_VALUE);
        table.setMaxHeight(Double.MAX_VALUE);
        table.getItems().setAll(requestRows);

        TableColumn<RequestDialogRow, String> articleCol = new TableColumn<>(COLUMN_HEADERS.get(0));
        configureColumn(articleCol, COL_ARTICLE_MIN, COL_ARTICLE_PREF, false);
        articleCol.setCellValueFactory(
                cell ->
                        new SimpleStringProperty(
                                displayOrDash(cell.getValue().stockRow().article())));
        articleCol.setCellFactory(column -> tooltipTextCell());

        TableColumn<RequestDialogRow, String> nameCol = new TableColumn<>(COLUMN_HEADERS.get(1));
        configureColumn(nameCol, COL_NAME_MIN, COL_NAME_PREF, true);
        nameCol.setCellValueFactory(
                cell ->
                        new SimpleStringProperty(displayOrDash(cell.getValue().stockRow().name())));
        nameCol.setCellFactory(column -> tooltipTextCell());

        TableColumn<RequestDialogRow, String> colorCol = new TableColumn<>(COLUMN_HEADERS.get(2));
        configureColumn(colorCol, COL_COLOR_MIN, COL_COLOR_PREF, false);
        colorCol.setCellValueFactory(
                cell ->
                        new SimpleStringProperty(
                                displayOrDash(cell.getValue().stockRow().color())));
        colorCol.setCellFactory(column -> tooltipTextCell());

        TableColumn<RequestDialogRow, String> sizeCol = new TableColumn<>(COLUMN_HEADERS.get(3));
        configureColumn(sizeCol, COL_SIZE_MIN, COL_SIZE_PREF, false);
        sizeCol.setCellValueFactory(
                cell ->
                        new SimpleStringProperty(
                                displayOrDash(cell.getValue().stockRow().size())));
        sizeCol.setCellFactory(column -> tooltipTextCell());

        TableColumn<RequestDialogRow, String> cellCol = new TableColumn<>(COLUMN_HEADERS.get(4));
        configureColumn(cellCol, COL_CELL_MIN, COL_CELL_PREF, false);
        cellCol.setCellValueFactory(
                cell ->
                        new SimpleStringProperty(
                                displayOrDash(cell.getValue().stockRow().cellCode())));
        cellCol.setCellFactory(column -> tooltipTextCell());

        TableColumn<RequestDialogRow, String> availableCol =
                new TableColumn<>(COLUMN_HEADERS.get(5));
        configureColumn(availableCol, COL_AVAILABLE_MIN, COL_AVAILABLE_PREF, false);
        availableCol.setCellValueFactory(
                cell ->
                        new SimpleStringProperty(
                                DecimalUiFormat.formatRu(
                                        cell.getValue().stockRow().availableQuantity())));

        TableColumn<RequestDialogRow, String> qtyCol = new TableColumn<>(COLUMN_HEADERS.get(6));
        configureColumn(qtyCol, COL_QTY_MIN, COL_QTY_PREF, false);
        qtyCol.setCellValueFactory(cell -> cell.getValue().quantityTextProperty());
        qtyCol.setCellFactory(
                column ->
                        new TableCell<>() {
                            private final TextField field = new TextField();
                            private RequestDialogRow boundRow;
                            private final javafx.beans.value.ChangeListener<Boolean> invalidListener =
                                    (obs, o, invalid) -> applyInvalidStyle(Boolean.TRUE.equals(invalid));

                            {
                                field.setMaxWidth(Double.MAX_VALUE);
                                field.setEditable(true);
                                field.textProperty()
                                        .addListener(
                                                (obs, o, n) -> {
                                                    RequestDialogRow row =
                                                            getTableRow() == null
                                                                    ? null
                                                                    : getTableRow().getItem();
                                                    if (row != null
                                                            && !Objects.equals(
                                                                    row.quantityTextProperty()
                                                                            .get(),
                                                                    n)) {
                                                        row.quantityTextProperty().set(n);
                                                    }
                                                    if (row != null) {
                                                        row.clearQuantityInvalid();
                                                    }
                                                    totalsLabel.setText(
                                                            "Итого: "
                                                                    + formatRequestQuantityTotalsByUnit(
                                                                            requestRows));
                                                });
                            }

                            @Override
                            protected void updateItem(String item, boolean empty) {
                                super.updateItem(item, empty);
                                unbindInvalid();
                                if (empty
                                        || getTableRow() == null
                                        || getTableRow().getItem() == null) {
                                    setGraphic(null);
                                    return;
                                }
                                RequestDialogRow row = getTableRow().getItem();
                                boundRow = row;
                                row.quantityInvalidProperty().addListener(invalidListener);
                                if (!Objects.equals(
                                        field.getText(), row.quantityTextProperty().get())) {
                                    field.setText(row.quantityTextProperty().get());
                                }
                                applyInvalidStyle(row.isQuantityInvalid());
                                setGraphic(field);
                            }

                            private void unbindInvalid() {
                                if (boundRow != null) {
                                    boundRow.quantityInvalidProperty()
                                            .removeListener(invalidListener);
                                    boundRow = null;
                                }
                            }

                            private void applyInvalidStyle(boolean invalid) {
                                if (invalid) {
                                    if (!field.getStyleClass().contains(INPUT_ERROR_STYLE)) {
                                        field.getStyleClass().add(INPUT_ERROR_STYLE);
                                    }
                                } else {
                                    field.getStyleClass().remove(INPUT_ERROR_STYLE);
                                }
                            }
                        });

        TableColumn<RequestDialogRow, String> unitCol = new TableColumn<>(COLUMN_HEADERS.get(7));
        configureColumn(unitCol, COL_UNIT_MIN, COL_UNIT_PREF, false);
        unitCol.setCellValueFactory(
                cell ->
                        new SimpleStringProperty(
                                displayOrDash(cell.getValue().stockRow().unitOfMeasure())));

        table.getColumns()
                .setAll(
                        articleCol,
                        nameCol,
                        colorCol,
                        sizeCol,
                        cellCol,
                        availableCol,
                        qtyCol,
                        unitCol);
        bindNameColumnToFreeWidth(
                table,
                nameCol,
                List.of(
                        articleCol,
                        colorCol,
                        sizeCol,
                        cellCol,
                        availableCol,
                        qtyCol,
                        unitCol));

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add(MESSAGE_ERROR_STYLE);
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(Double.MAX_VALUE);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        VBox root = new VBox(10);
        root.setPadding(new Insets(12));
        root.setFillWidth(true);
        root.setMaxWidth(Double.MAX_VALUE);
        root.setMaxHeight(Double.MAX_VALUE);
        root.setAlignment(Pos.TOP_LEFT);
        VBox.setVgrow(table, Priority.ALWAYS);
        root.getChildren().addAll(destinationLabel, totalsLabel, table, errorLabel);
        dialog.getDialogPane().setContent(root);
        dialog.getDialogPane().setExpandableContent(null);

        RequestDialogSession session =
                new RequestDialogSession(dialog, submitType, requestRows, table, errorLabel);
        dialog.setOnShown(
                e -> {
                    Button submitButton =
                            (Button) dialog.getDialogPane().lookupButton(submitType);
                    Button cancelButton =
                            (Button) dialog.getDialogPane().lookupButton(cancelType);
                    if (submitButton != null) {
                        submitButton.getStyleClass().add("tmp-button-primary");
                        submitButton.addEventFilter(
                                ActionEvent.ACTION,
                                event -> {
                                    try {
                                        List<StockMoveLine> lines = session.requireSubmission();
                                        session.clearError();
                                        Consumer<List<StockMoveLine>> handler =
                                                session.onValidSubmit();
                                        if (handler != null) {
                                            event.consume();
                                            handler.accept(lines);
                                        }
                                    } catch (IllegalArgumentException ex) {
                                        event.consume();
                                        session.showError(ex.getMessage());
                                        table.refresh();
                                    }
                                });
                    }
                    if (cancelButton != null) {
                        cancelButton.getStyleClass().add("tmp-button-secondary");
                    }
                    redistributeNameColumnWidth(
                            table,
                            nameCol,
                            List.of(
                                    articleCol,
                                    colorCol,
                                    sizeCol,
                                    cellCol,
                                    availableCol,
                                    qtyCol,
                                    unitCol));
                });

        return session;
    }

    /**
     * Aggregates per-row request quantities into one line per {@code materialReferenceId},
     * preserving encounter order and exact {@link BigDecimal} sums.
     */
    public static List<StockMoveLine> aggregateByMaterial(List<StockMoveLine> lines) {
        Objects.requireNonNull(lines, "lines");
        if (lines.isEmpty()) {
            return List.of();
        }
        Map<UUID, StockMoveLine> aggregated = new LinkedHashMap<>();
        for (StockMoveLine line : lines) {
            StockMoveLine existing = aggregated.get(line.materialReferenceId());
            if (existing == null) {
                aggregated.put(line.materialReferenceId(), line);
            } else {
                aggregated.put(
                        line.materialReferenceId(),
                        new StockMoveLine(
                                existing.warehouseId(),
                                existing.sourceStorageCellId(),
                                existing.materialReferenceId(),
                                existing.availableQuantity().add(line.availableQuantity()),
                                existing.quantity().add(line.quantity()),
                                existing.materialLabel(),
                                existing.cellCode()));
            }
        }
        return List.copyOf(aggregated.values());
    }

    public static BigDecimal parseRequestQuantity(String raw, BigDecimal available) {
        Objects.requireNonNull(available, "available");
        BigDecimal qty = DecimalQuantityParser.parsePositive(raw, "количество");
        if (qty.compareTo(available) > 0) {
            throw new IllegalArgumentException(
                    "Количество не может превышать доступный остаток ("
                            + DecimalUiFormat.formatRu(available)
                            + ")");
        }
        return qty;
    }

    public static String displayOrDash(String value) {
        return value == null || value.isBlank() ? "—" : value.trim();
    }

    static String formatRequestQuantityTotalsByUnit(List<RequestDialogRow> rows) {
        Objects.requireNonNull(rows, "rows");
        if (rows.isEmpty()) {
            return "";
        }
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (RequestDialogRow row : rows) {
            String unit = displayOrDash(row.stockRow().unitOfMeasure());
            BigDecimal qty = tryParseQuantity(row.quantityTextProperty().get());
            if (qty == null) {
                continue;
            }
            totals.merge(unit, qty, BigDecimal::add);
        }
        return formatTotalsMap(totals);
    }

    private static BigDecimal tryParseQuantity(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return DecimalQuantityParser.parsePositive(raw, "количество");
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String formatTotalsMap(Map<String, BigDecimal> totals) {
        if (totals.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, BigDecimal> entry : totals.entrySet()) {
            if (!builder.isEmpty()) {
                builder.append("; ");
            }
            builder.append(DecimalUiFormat.formatRu(entry.getValue()));
            builder.append(' ');
            builder.append(entry.getKey());
            if (!entry.getKey().endsWith(".")) {
                builder.append('.');
            }
        }
        return builder.toString();
    }

    private static TableCell<RequestDialogRow, String> tooltipTextCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                    return;
                }
                setText(item);
                setTooltip(new Tooltip(item));
            }
        };
    }

    private static void configureColumn(
            TableColumn<?, ?> column, double minWidth, double prefWidth, boolean growable) {
        column.setMinWidth(minWidth);
        column.setPrefWidth(prefWidth);
        column.setResizable(true);
        if (growable) {
            column.setMaxWidth(Double.MAX_VALUE);
        } else {
            column.setMaxWidth(Math.max(prefWidth * 1.6, minWidth + 40));
        }
    }

    private static void bindNameColumnToFreeWidth(
            TableView<?> table,
            TableColumn<?, ?> nameCol,
            List<? extends TableColumn<?, ?>> otherColumns) {
        table.widthProperty()
                .addListener(
                        (obs, oldWidth, newWidth) ->
                                redistributeNameColumnWidth(table, nameCol, otherColumns));
        table.layoutBoundsProperty()
                .addListener(
                        (obs, o, n) -> redistributeNameColumnWidth(table, nameCol, otherColumns));
    }

    private static void redistributeNameColumnWidth(
            TableView<?> table,
            TableColumn<?, ?> nameCol,
            List<? extends TableColumn<?, ?>> otherColumns) {
        double reserved = TABLE_SIDE_INSETS;
        for (TableColumn<?, ?> column : otherColumns) {
            reserved += Math.max(column.getWidth(), column.getPrefWidth());
        }
        double available = table.getWidth() - reserved;
        double next = Math.max(nameCol.getMinWidth(), available);
        if (Math.abs(nameCol.getPrefWidth() - next) > 0.5) {
            nameCol.setPrefWidth(next);
        }
    }

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP",
            justification = "JavaFX Dialog must be returned for showAndWait by the caller")
    public static final class RequestDialogSession {
        private final Dialog<ButtonType> dialog;
        private final ButtonType submitType;
        private final List<RequestDialogRow> requestRows;
        private final TableView<RequestDialogRow> table;
        private final Label errorLabel;
        private Consumer<List<StockMoveLine>> onValidSubmit;

        RequestDialogSession(
                Dialog<ButtonType> dialog,
                ButtonType submitType,
                List<RequestDialogRow> requestRows,
                TableView<RequestDialogRow> table,
                Label errorLabel) {
            this.dialog = dialog;
            this.submitType = submitType;
            this.requestRows = List.copyOf(requestRows);
            this.table = table;
            this.errorLabel = errorLabel;
        }

        public Dialog<ButtonType> dialog() {
            return dialog;
        }

        public ButtonType submitType() {
            return submitType;
        }

        public void setOnValidSubmit(Consumer<List<StockMoveLine>> onValidSubmit) {
            this.onValidSubmit = onValidSubmit;
        }

        Consumer<List<StockMoveLine>> onValidSubmit() {
            return onValidSubmit;
        }

        public void close() {
            dialog.close();
        }

        public void showError(String message) {
            errorLabel.setText(message == null ? "" : message);
            boolean visible = message != null && !message.isBlank();
            errorLabel.setVisible(visible);
            errorLabel.setManaged(visible);
        }

        public void clearError() {
            showError("");
        }

        public boolean isErrorVisible() {
            return errorLabel.isVisible();
        }

        public String errorText() {
            return errorLabel.getText() == null ? "" : errorLabel.getText();
        }

        public List<RequestDialogRow> rows() {
            return requestRows;
        }

        public String headerText() {
            return dialog.getHeaderText();
        }

        public List<String> columnHeaders() {
            TableView<?> found = findTable();
            if (found == null) {
                return List.of();
            }
            List<String> headers = new ArrayList<>();
            for (TableColumn<?, ?> column : found.getColumns()) {
                headers.add(column.getText());
            }
            return headers;
        }

        public List<String> buttonLabels() {
            List<String> labels = new ArrayList<>();
            for (ButtonType type : dialog.getDialogPane().getButtonTypes()) {
                labels.add(type.getText());
            }
            return labels;
        }

        public TableView<?> tableView() {
            TableView<?> found = findTable();
            if (found == null) {
                throw new IllegalStateException("Request dialog TableView not found");
            }
            return found;
        }

        /**
         * Validates quantities. Marks invalid quantity fields with {@link #INPUT_ERROR_STYLE}. Throws
         * {@link IllegalArgumentException} on the first invalid input. Does not create documents.
         */
        public List<StockMoveLine> requireSubmission() {
            for (RequestDialogRow requestRow : requestRows) {
                requestRow.clearQuantityInvalid();
            }
            List<StockMoveLine> lines = new ArrayList<>();
            IllegalArgumentException firstError = null;
            for (RequestDialogRow requestRow : requestRows) {
                try {
                    BigDecimal qty =
                            parseRequestQuantity(
                                    requestRow.quantityTextProperty().get(),
                                    requestRow.stockRow().availableQuantity());
                    lines.add(StockMoveLine.from(requestRow.stockRow(), qty));
                } catch (IllegalArgumentException ex) {
                    requestRow.markQuantityInvalid();
                    if (firstError == null) {
                        firstError = ex;
                    }
                }
            }
            if (firstError != null) {
                throw firstError;
            }
            return List.copyOf(lines);
        }

        @SuppressWarnings("unchecked")
        private TableView<?> findTable() {
            if (table != null) {
                return table;
            }
            javafx.scene.Node content = dialog.getDialogPane().getContent();
            if (content instanceof TableView<?> tableView) {
                return tableView;
            }
            if (content instanceof VBox root) {
                for (javafx.scene.Node node : root.getChildren()) {
                    if (node instanceof TableView<?> tableView) {
                        return tableView;
                    }
                }
            }
            return null;
        }
    }

    static final class RequestDialogRow {
        private final StockRow stockRow;
        private final StringProperty quantityText;
        private final BooleanProperty quantityInvalid = new SimpleBooleanProperty(false);

        RequestDialogRow(StockRow stockRow) {
            this.stockRow = stockRow;
            this.quantityText =
                    new SimpleStringProperty(
                            DecimalUiFormat.formatRu(stockRow.availableQuantity()));
            this.quantityText.addListener((obs, o, n) -> clearQuantityInvalid());
        }

        StockRow stockRow() {
            return stockRow;
        }

        StringProperty quantityTextProperty() {
            return quantityText;
        }

        BooleanProperty quantityInvalidProperty() {
            return quantityInvalid;
        }

        boolean isQuantityInvalid() {
            return quantityInvalid.get();
        }

        void markQuantityInvalid() {
            quantityInvalid.set(true);
        }

        void clearQuantityInvalid() {
            quantityInvalid.set(false);
        }
    }
}
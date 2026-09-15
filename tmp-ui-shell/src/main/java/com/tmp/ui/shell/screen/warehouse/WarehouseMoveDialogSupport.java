package com.tmp.ui.shell.screen.warehouse;

import com.tmp.ui.shell.order.DecimalQuantityParser;
import com.tmp.ui.shell.order.DecimalUiFormat;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.StockMoveLine;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.StockRow;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

/**
 * Production TableView move dialog for Stocks (1 and N rows) plus pure helpers for totals, display
 * blanks, and quantity validation.
 */
public final class WarehouseMoveDialogSupport {

    /** Stable id for runtime DEBUG identification of this dialog implementation. */
    public static final String IMPLEMENTATION_ID = "WarehouseMoveDialogSupport-table-v1";

    public static final String TITLE = "Перемещение материалов";
    public static final String CANCEL_BUTTON = "Отмена";
    public static final String FILL_AVAILABLE_BUTTON = "Всё доступное";
    public static final String SUBMIT_SAME_WAREHOUSE_BUTTON = "Переместить";
    public static final String SUBMIT_INTER_WAREHOUSE_BUTTON = "Отправить";

    public static final List<String> COLUMN_HEADERS =
            List.of(
                    "Артикул",
                    "Наименование",
                    "Цвет",
                    "Размер",
                    "Откуда",
                    "Доступно",
                    "Переместить",
                    "Ед.");

    private static final Logger LOGGER =
            System.getLogger(WarehouseMoveDialogSupport.class.getName());

    private WarehouseMoveDialogSupport() {}

    /** Header text: {@code Выбрано позиций: N}. */
    public static String selectedCountHeader(int selectedCount) {
        return "Выбрано позиций: " + selectedCount;
    }

    /**
     * Builds the production TableView move dialog used by Stocks. Same dialog for 1 and N rows.
     * Does not show the dialog — caller invokes {@link Dialog#showAndWait()}.
     */
    public static MoveDialogSession createMoveDialog(
            List<StockRow> selected,
            UUID sourceWarehouseId,
            String sourceWarehouseLabel,
            List<WarehouseChoice> accessibleWarehouses,
            Function<UUID, List<StorageCellChoice>> destinationCellsLoader) {
        Objects.requireNonNull(selected, "selected");
        Objects.requireNonNull(sourceWarehouseId, "sourceWarehouseId");
        Objects.requireNonNull(sourceWarehouseLabel, "sourceWarehouseLabel");
        Objects.requireNonNull(accessibleWarehouses, "accessibleWarehouses");
        Objects.requireNonNull(destinationCellsLoader, "destinationCellsLoader");
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("selected must not be empty");
        }

        LOGGER.log(
                Level.DEBUG,
                "Opening WarehouseMoveDialogSupport {0}",
                IMPLEMENTATION_ID);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(TITLE);
        dialog.setHeaderText(selectedCountHeader(selected.size()));
        dialog.setResizable(true);
        ButtonType submitType =
                new ButtonType(SUBMIT_SAME_WAREHOUSE_BUTTON, ButtonBar.ButtonData.OK_DONE);
        ButtonType fillAvailableType =
                new ButtonType(FILL_AVAILABLE_BUTTON, ButtonBar.ButtonData.LEFT);
        ButtonType cancelType = new ButtonType(CANCEL_BUTTON, ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(cancelType, fillAvailableType, submitType);
        dialog.getDialogPane().getStyleClass().add("tmp-dialog");
        dialog.getDialogPane().setPrefWidth(920);

        Label sourceLabel = new Label("Откуда: " + sourceWarehouseLabel);
        Label totalsLabel =
                new Label("Итого: " + formatQuantityTotalsByUnit(selected));
        ComboBox<WarehouseChoice> destinationWarehouse = new ComboBox<>();
        destinationWarehouse.getItems().setAll(accessibleWarehouses);
        destinationWarehouse.setMaxWidth(Double.MAX_VALUE);
        ComboBox<StorageCellChoice> destinationCell = new ComboBox<>();
        destinationCell.setMaxWidth(Double.MAX_VALUE);
        Label destWarehouseCaption = new Label("Склад назначения:");
        Label destCellCaption = new Label("Ячейка назначения:");

        TableView<MoveDialogRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(280);
        List<MoveDialogRow> moveRows = new ArrayList<>();
        for (StockRow row : selected) {
            moveRows.add(new MoveDialogRow(row));
        }
        table.getItems().setAll(moveRows);

        TableColumn<MoveDialogRow, String> articleCol = new TableColumn<>(COLUMN_HEADERS.get(0));
        articleCol.setCellValueFactory(
                cell ->
                        new SimpleStringProperty(
                                displayOrDash(cell.getValue().stockRow().article())));
        TableColumn<MoveDialogRow, String> nameCol = new TableColumn<>(COLUMN_HEADERS.get(1));
        nameCol.setCellValueFactory(
                cell ->
                        new SimpleStringProperty(
                                displayOrDash(cell.getValue().stockRow().name())));
        TableColumn<MoveDialogRow, String> colorCol = new TableColumn<>(COLUMN_HEADERS.get(2));
        colorCol.setCellValueFactory(
                cell ->
                        new SimpleStringProperty(
                                displayOrDash(cell.getValue().stockRow().color())));
        TableColumn<MoveDialogRow, String> sizeCol = new TableColumn<>(COLUMN_HEADERS.get(3));
        sizeCol.setCellValueFactory(
                cell ->
                        new SimpleStringProperty(
                                displayOrDash(cell.getValue().stockRow().size())));
        TableColumn<MoveDialogRow, String> fromCol = new TableColumn<>(COLUMN_HEADERS.get(4));
        fromCol.setCellValueFactory(
                cell ->
                        new SimpleStringProperty(
                                displayOrDash(cell.getValue().stockRow().cellCode())));
        TableColumn<MoveDialogRow, String> availableCol = new TableColumn<>(COLUMN_HEADERS.get(5));
        availableCol.setCellValueFactory(
                cell ->
                        new SimpleStringProperty(
                                DecimalUiFormat.formatRu(
                                        cell.getValue().stockRow().availableQuantity())));
        TableColumn<MoveDialogRow, String> qtyCol = new TableColumn<>(COLUMN_HEADERS.get(6));
        qtyCol.setCellValueFactory(cell -> cell.getValue().quantityTextProperty());
        qtyCol.setCellFactory(
                column ->
                        new TableCell<>() {
                            private final TextField field = new TextField();

                            {
                                field.textProperty()
                                        .addListener(
                                                (obs, o, n) -> {
                                                    MoveDialogRow row =
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
                                                });
                            }

                            @Override
                            protected void updateItem(String item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty
                                        || getTableRow() == null
                                        || getTableRow().getItem() == null) {
                                    setGraphic(null);
                                    return;
                                }
                                MoveDialogRow row = getTableRow().getItem();
                                if (!Objects.equals(
                                        field.getText(), row.quantityTextProperty().get())) {
                                    field.setText(row.quantityTextProperty().get());
                                }
                                setGraphic(field);
                            }
                        });
        TableColumn<MoveDialogRow, String> unitCol = new TableColumn<>(COLUMN_HEADERS.get(7));
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
                        fromCol,
                        availableCol,
                        qtyCol,
                        unitCol);

        Runnable updateDestinationCellVisibility =
                () -> {
                    WarehouseChoice dest = destinationWarehouse.getValue();
                    boolean same = dest != null && sourceWarehouseId.equals(dest.id());
                    destCellCaption.setVisible(same);
                    destCellCaption.setManaged(same);
                    destinationCell.setVisible(same);
                    destinationCell.setManaged(same);
                    destinationCell.setDisable(!same);
                    destinationCell.getItems().clear();
                    if (same) {
                        destinationCell
                                .getItems()
                                .setAll(destinationCellsLoader.apply(dest.id()));
                    }
                    Button submitButton =
                            (Button) dialog.getDialogPane().lookupButton(submitType);
                    if (submitButton != null) {
                        submitButton.setText(
                                same
                                        ? SUBMIT_SAME_WAREHOUSE_BUTTON
                                        : SUBMIT_INTER_WAREHOUSE_BUTTON);
                    }
                };
        destinationWarehouse
                .valueProperty()
                .addListener((obs, oldValue, newValue) -> updateDestinationCellVisibility.run());

        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        form.setPadding(new Insets(8));
        form.add(sourceLabel, 0, 0, 2, 1);
        form.add(totalsLabel, 0, 1, 2, 1);
        form.add(destWarehouseCaption, 0, 2);
        form.add(destinationWarehouse, 1, 2);
        form.add(destCellCaption, 0, 3);
        form.add(destinationCell, 1, 3);
        form.add(table, 0, 4, 2, 1);
        dialog.getDialogPane().setContent(form);

        dialog.setOnShown(
                e -> {
                    Button submitButton =
                            (Button) dialog.getDialogPane().lookupButton(submitType);
                    Button cancelButton =
                            (Button) dialog.getDialogPane().lookupButton(cancelType);
                    Button fillButton =
                            (Button) dialog.getDialogPane().lookupButton(fillAvailableType);
                    if (submitButton != null) {
                        submitButton.getStyleClass().add("tmp-button-primary");
                    }
                    if (cancelButton != null) {
                        cancelButton.getStyleClass().add("tmp-button-secondary");
                    }
                    if (fillButton != null) {
                        fillButton.getStyleClass().add("tmp-button-secondary");
                        fillButton.addEventFilter(
                                javafx.event.ActionEvent.ACTION,
                                event -> {
                                    event.consume();
                                    for (MoveDialogRow row : moveRows) {
                                        row.quantityTextProperty()
                                                .set(
                                                        DecimalUiFormat.formatRu(
                                                                row.stockRow()
                                                                        .availableQuantity()));
                                    }
                                    table.refresh();
                                });
                    }
                    updateDestinationCellVisibility.run();
                });

        return new MoveDialogSession(
                dialog, submitType, sourceWarehouseId, destinationWarehouse, destinationCell, moveRows);
    }

    /**
     * Sums quantities per unit of measure (never across different UoMs). Examples: {@code "12 м.; 3
     * шт."}, {@code "12 м."}.
     */
    public static String formatQuantityTotalsByUnit(List<StockRow> rows) {
        Objects.requireNonNull(rows, "rows");
        if (rows.isEmpty()) {
            return "";
        }
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (StockRow row : rows) {
            String unit = displayOrDash(row.unitOfMeasure());
            BigDecimal qty = row.availableQuantity();
            totals.merge(unit, qty, BigDecimal::add);
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

    /** Blank or null display fields become an em dash. */
    public static String displayOrDash(String value) {
        return value == null || value.isBlank() ? "—" : value.trim();
    }

    /**
     * Parses move quantity: accepts {@code 0,5} / {@code 0.5}; must be {@code > 0} and {@code <=
     * available}.
     */
    public static BigDecimal parseMoveQuantity(String raw, BigDecimal available) {
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

    /** Same-warehouse destination cell must differ from the source cell of a row. */
    public static void validateNotSelfMove(UUID sourceStorageCellId, UUID destinationStorageCellId) {
        Objects.requireNonNull(sourceStorageCellId, "sourceStorageCellId");
        Objects.requireNonNull(destinationStorageCellId, "destinationStorageCellId");
        if (destinationStorageCellId.equals(sourceStorageCellId)) {
            throw new IllegalArgumentException(
                    "Ячейка назначения должна отличаться от ячейки источника.");
        }
    }

    /**
     * Built move dialog: UI session plus helpers to collect a validated submission after OK.
     */
    public static final class MoveDialogSession {
        private final Dialog<ButtonType> dialog;
        private final ButtonType submitType;
        private final UUID sourceWarehouseId;
        private final ComboBox<WarehouseChoice> destinationWarehouse;
        private final ComboBox<StorageCellChoice> destinationCell;
        private final List<MoveDialogRow> moveRows;

        MoveDialogSession(
                Dialog<ButtonType> dialog,
                ButtonType submitType,
                UUID sourceWarehouseId,
                ComboBox<WarehouseChoice> destinationWarehouse,
                ComboBox<StorageCellChoice> destinationCell,
                List<MoveDialogRow> moveRows) {
            this.dialog = dialog;
            this.submitType = submitType;
            this.sourceWarehouseId = sourceWarehouseId;
            this.destinationWarehouse = destinationWarehouse;
            this.destinationCell = destinationCell;
            this.moveRows = List.copyOf(moveRows);
        }

        public Dialog<ButtonType> dialog() {
            return dialog;
        }

        public ButtonType submitType() {
            return submitType;
        }

        public String headerText() {
            return dialog.getHeaderText();
        }

        public List<String> columnHeaders() {
            TableView<?> table = findTable();
            if (table == null) {
                return List.of();
            }
            List<String> headers = new ArrayList<>();
            for (TableColumn<?, ?> column : table.getColumns()) {
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

        /**
         * Validates destination and quantities after the user confirms OK. Throws {@link
         * IllegalArgumentException} for missing destination or invalid quantities.
         */
        public MoveSubmission requireSubmission() {
            WarehouseChoice destWh = destinationWarehouse.getValue();
            if (destWh == null) {
                throw new IllegalArgumentException("Выберите склад назначения.");
            }
            List<StockMoveLine> lines = new ArrayList<>();
            for (MoveDialogRow moveRow : moveRows) {
                BigDecimal qty =
                        parseMoveQuantity(
                                moveRow.quantityTextProperty().get(),
                                moveRow.stockRow().availableQuantity());
                lines.add(StockMoveLine.from(moveRow.stockRow(), qty));
            }
            if (destWh.id().equals(sourceWarehouseId)) {
                StorageCellChoice cell = destinationCell.getValue();
                if (cell == null) {
                    throw new IllegalArgumentException("Выберите ячейку назначения.");
                }
                for (StockMoveLine line : lines) {
                    validateNotSelfMove(line.sourceStorageCellId(), cell.id());
                }
                return new MoveSubmission(lines, destWh.id(), cell.id(), true);
            }
            return new MoveSubmission(lines, destWh.id(), null, false);
        }

        @SuppressWarnings("unchecked")
        private TableView<?> findTable() {
            if (dialog.getDialogPane().getContent() instanceof GridPane form) {
                for (javafx.scene.Node node : form.getChildren()) {
                    if (node instanceof TableView<?> tableView) {
                        return tableView;
                    }
                }
            }
            return null;
        }
    }

    /** Validated move lines ready for ViewModel execute methods. */
    public record MoveSubmission(
            List<StockMoveLine> lines,
            UUID destinationWarehouseId,
            UUID destinationStorageCellId,
            boolean sameWarehouse) {

        public MoveSubmission {
            Objects.requireNonNull(lines, "lines");
            Objects.requireNonNull(destinationWarehouseId, "destinationWarehouseId");
            if (sameWarehouse) {
                Objects.requireNonNull(
                        destinationStorageCellId, "destinationStorageCellId");
            }
            lines = List.copyOf(lines);
        }
    }

    static final class MoveDialogRow {
        private final StockRow stockRow;
        private final StringProperty quantityText;

        MoveDialogRow(StockRow stockRow) {
            this.stockRow = stockRow;
            this.quantityText =
                    new SimpleStringProperty(
                            DecimalUiFormat.formatRu(stockRow.availableQuantity()));
        }

        StockRow stockRow() {
            return stockRow;
        }

        StringProperty quantityTextProperty() {
            return quantityText;
        }
    }
}

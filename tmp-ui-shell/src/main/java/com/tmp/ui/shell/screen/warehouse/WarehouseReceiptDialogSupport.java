package com.tmp.ui.shell.screen.warehouse;

import com.tmp.ui.shell.order.DecimalQuantityParser;
import com.tmp.ui.shell.order.DecimalUiFormat;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Table-style receipt dialog for Warehouse Workspace (Поступление). Does not require stock row
 * selection — materials appear on the warehouse.
 */
public final class WarehouseReceiptDialogSupport {

    public static final String TITLE = "Поступление материалов";
    public static final String CANCEL_BUTTON = "Отмена";
    public static final String SUBMIT_BUTTON = "Оформить поступление";
    public static final String ADD_LINE_BUTTON = "Добавить строку";

    public static final List<String> COLUMN_HEADERS =
            List.of(
                    "Материал",
                    "Наименование",
                    "Цвет",
                    "Размер",
                    "Количество",
                    "Ед.",
                    "Склад",
                    "Ячейка");

    public static final int MATERIAL_COLUMN = 0;
    public static final int NAME_COLUMN = 1;
    public static final int COLOR_COLUMN = 2;
    public static final int SIZE_COLUMN = 3;
    public static final int QUANTITY_COLUMN = 4;
    public static final int UNIT_COLUMN = 5;
    public static final int WAREHOUSE_COLUMN = 6;
    public static final int CELL_COLUMN = 7;

    private WarehouseReceiptDialogSupport() {}

    public static ReceiptDialogSession createReceiptDialog(
            List<WarehouseChoice> warehouses,
            List<String> unitsOfMeasure,
            Function<UUID, List<StorageCellChoice>> cellsLoader) {
        Objects.requireNonNull(warehouses, "warehouses");
        Objects.requireNonNull(unitsOfMeasure, "unitsOfMeasure");
        Objects.requireNonNull(cellsLoader, "cellsLoader");

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(TITLE);
        dialog.setHeaderText("Новое поступление");
        dialog.setResizable(true);
        ButtonType submitType = new ButtonType(SUBMIT_BUTTON, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType(CANCEL_BUTTON, ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(cancelType, submitType);
        dialog.getDialogPane().getStyleClass().add("tmp-dialog");
        dialog.getDialogPane().setPrefSize(1080, 440);
        dialog.getDialogPane().setMinSize(900, 340);

        ObservableList<ReceiptDialogRow> rows = FXCollections.observableArrayList();
        rows.add(new ReceiptDialogRow());
        TableView<ReceiptDialogRow> table = new TableView<>(rows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(260);
        table.setPlaceholder(new Label("Нет строк поступления"));
        table.setEditable(false);

        TableColumn<ReceiptDialogRow, String> materialCol =
                new TableColumn<>(COLUMN_HEADERS.get(MATERIAL_COLUMN));
        materialCol.setCellValueFactory(c -> c.getValue().materialTextProperty());
        materialCol.setCellFactory(col -> textFieldCell(row -> row.materialTextProperty()));
        TableColumn<ReceiptDialogRow, String> nameCol =
                new TableColumn<>(COLUMN_HEADERS.get(NAME_COLUMN));
        nameCol.setCellValueFactory(c -> c.getValue().nameTextProperty());
        nameCol.setCellFactory(col -> textFieldCell(row -> row.nameTextProperty()));
        TableColumn<ReceiptDialogRow, String> colorCol =
                new TableColumn<>(COLUMN_HEADERS.get(COLOR_COLUMN));
        colorCol.setCellValueFactory(c -> c.getValue().colorTextProperty());
        colorCol.setCellFactory(col -> textFieldCell(row -> row.colorTextProperty()));
        TableColumn<ReceiptDialogRow, String> sizeCol =
                new TableColumn<>(COLUMN_HEADERS.get(SIZE_COLUMN));
        sizeCol.setCellValueFactory(c -> c.getValue().sizeTextProperty());
        sizeCol.setCellFactory(col -> textFieldCell(row -> row.sizeTextProperty()));
        TableColumn<ReceiptDialogRow, String> qtyCol =
                new TableColumn<>(COLUMN_HEADERS.get(QUANTITY_COLUMN));
        qtyCol.setCellValueFactory(c -> c.getValue().quantityTextProperty());
        qtyCol.setCellFactory(col -> textFieldCell(row -> row.quantityTextProperty()));
        TableColumn<ReceiptDialogRow, String> unitCol =
                new TableColumn<>(COLUMN_HEADERS.get(UNIT_COLUMN));
        unitCol.setCellValueFactory(
                c ->
                        new SimpleStringProperty(
                                c.getValue().unitOfMeasure() == null
                                        ? ""
                                        : c.getValue().unitOfMeasure()));
        unitCol.setCellFactory(col -> unitComboCell(unitsOfMeasure));
        TableColumn<ReceiptDialogRow, String> warehouseCol =
                new TableColumn<>(COLUMN_HEADERS.get(WAREHOUSE_COLUMN));
        warehouseCol.setCellValueFactory(
                c ->
                        new SimpleStringProperty(
                                c.getValue().warehouse() == null
                                        ? ""
                                        : c.getValue().warehouse().label()));
        warehouseCol.setCellFactory(col -> warehouseComboCell(warehouses));
        TableColumn<ReceiptDialogRow, String> cellCol =
                new TableColumn<>(COLUMN_HEADERS.get(CELL_COLUMN));
        cellCol.setCellValueFactory(
                c ->
                        new SimpleStringProperty(
                                c.getValue().cell() == null ? "" : c.getValue().cell().label()));
        cellCol.setCellFactory(col -> storageCellComboCell(cellsLoader));

        table.getColumns()
                .setAll(
                        materialCol,
                        nameCol,
                        colorCol,
                        sizeCol,
                        qtyCol,
                        unitCol,
                        warehouseCol,
                        cellCol);

        Button addLine = new Button(ADD_LINE_BUTTON);
        addLine.getStyleClass().add("tmp-button-secondary");
        addLine.setOnAction(e -> rows.add(new ReceiptDialogRow()));

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("tmp-message-error");
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(Double.MAX_VALUE);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        VBox root = new VBox(10, table, new HBox(addLine), errorLabel);
        root.setPadding(new Insets(12));
        VBox.setVgrow(table, Priority.ALWAYS);
        dialog.getDialogPane().setContent(root);

        ReceiptDialogSession session =
                new ReceiptDialogSession(dialog, submitType, rows, table, errorLabel);
        dialog.setOnShown(
                e -> {
                    Button submit = (Button) dialog.getDialogPane().lookupButton(submitType);
                    Button cancel = (Button) dialog.getDialogPane().lookupButton(cancelType);
                    if (submit != null) {
                        submit.getStyleClass().add("tmp-button-primary");
                        submit.addEventFilter(
                                ActionEvent.ACTION,
                                event -> {
                                    try {
                                        session.requireSubmission();
                                        session.clearError();
                                    } catch (IllegalArgumentException ex) {
                                        event.consume();
                                        session.showError(ex.getMessage());
                                    }
                                });
                    }
                    if (cancel != null) {
                        cancel.getStyleClass().add("tmp-button-secondary");
                    }
                });

        return session;
    }

    private static TableCell<ReceiptDialogRow, String> textFieldCell(
            Function<ReceiptDialogRow, StringProperty> property) {
        return new TableCell<>() {
            private final TextField field = new TextField();

            {
                field.setMaxWidth(Double.MAX_VALUE);
                field.textProperty()
                        .addListener(
                                (obs, o, n) -> {
                                    ReceiptDialogRow row = boundRow();
                                    if (row != null
                                            && !Objects.equals(property.apply(row).get(), n)) {
                                        property.apply(row).set(n);
                                    }
                                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                ReceiptDialogRow row = boundRow();
                if (empty || row == null) {
                    setGraphic(null);
                    return;
                }
                StringProperty prop = property.apply(row);
                if (!Objects.equals(field.getText(), prop.get())) {
                    field.setText(prop.get());
                }
                field.setDisable(false);
                field.setEditable(true);
                setGraphic(field);
            }

            private ReceiptDialogRow boundRow() {
                return getTableRow() == null ? null : getTableRow().getItem();
            }
        };
    }

    private static TableCell<ReceiptDialogRow, String> unitComboCell(List<String> unitsOfMeasure) {
        return new TableCell<>() {
            private final ComboBox<String> combo = new ComboBox<>();
            private boolean syncing;

            {
                combo.setItems(FXCollections.observableArrayList(unitsOfMeasure));
                combo.setMaxWidth(Double.MAX_VALUE);
                combo.valueProperty()
                        .addListener(
                                (obs, o, n) -> {
                                    if (syncing) {
                                        return;
                                    }
                                    ReceiptDialogRow row = boundRow();
                                    if (row != null) {
                                        row.setUnitOfMeasure(n);
                                    }
                                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                ReceiptDialogRow row = boundRow();
                if (empty || row == null) {
                    setGraphic(null);
                    return;
                }
                syncing = true;
                try {
                    if (!Objects.equals(combo.getValue(), row.unitOfMeasure())) {
                        combo.setValue(row.unitOfMeasure());
                    }
                } finally {
                    syncing = false;
                }
                combo.setDisable(false);
                setGraphic(combo);
            }

            private ReceiptDialogRow boundRow() {
                return getTableRow() == null ? null : getTableRow().getItem();
            }
        };
    }

    private static TableCell<ReceiptDialogRow, String> warehouseComboCell(
            List<WarehouseChoice> warehouses) {
        return new TableCell<>() {
            private final ComboBox<WarehouseChoice> combo = new ComboBox<>();
            private boolean syncing;

            {
                combo.setItems(FXCollections.observableArrayList(warehouses));
                combo.setMaxWidth(Double.MAX_VALUE);
                combo.valueProperty()
                        .addListener(
                                (obs, o, n) -> {
                                    if (syncing) {
                                        return;
                                    }
                                    ReceiptDialogRow row = boundRow();
                                    if (row == null) {
                                        return;
                                    }
                                    // No table.refresh() — refresh tears down TextField/ComboBox
                                    // graphics and leaves the row non-editable.
                                    row.applyWarehouseSelection(n);
                                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                ReceiptDialogRow row = boundRow();
                if (empty || row == null) {
                    setGraphic(null);
                    return;
                }
                syncing = true;
                try {
                    if (!Objects.equals(combo.getValue(), row.warehouse())) {
                        combo.setValue(row.warehouse());
                    }
                } finally {
                    syncing = false;
                }
                combo.setDisable(false);
                setGraphic(combo);
            }

            private ReceiptDialogRow boundRow() {
                return getTableRow() == null ? null : getTableRow().getItem();
            }
        };
    }

    private static TableCell<ReceiptDialogRow, String> storageCellComboCell(
            Function<UUID, List<StorageCellChoice>> cellsLoader) {
        return new TableCell<>() {
            private final ComboBox<StorageCellChoice> combo = new ComboBox<>();
            private boolean syncing;
            private ReceiptDialogRow bound;
            private final ChangeListener<WarehouseChoice> warehouseListener =
                    (obs, o, n) -> reloadCellsForBoundRow();

            {
                combo.setMaxWidth(Double.MAX_VALUE);
                combo.valueProperty()
                        .addListener(
                                (obs, o, n) -> {
                                    if (syncing) {
                                        return;
                                    }
                                    ReceiptDialogRow row = boundRow();
                                    if (row != null) {
                                        row.setCell(n);
                                    }
                                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                detachWarehouseListener();
                ReceiptDialogRow row = boundRow();
                if (empty || row == null) {
                    setGraphic(null);
                    return;
                }
                bound = row;
                bound.warehouseProperty().addListener(warehouseListener);
                reloadCells(row);
                setGraphic(combo);
            }

            private void reloadCellsForBoundRow() {
                ReceiptDialogRow row = bound;
                if (row == null) {
                    return;
                }
                reloadCells(row);
            }

            private void reloadCells(ReceiptDialogRow row) {
                WarehouseChoice warehouse = row.warehouse();
                boolean enabled = warehouse != null;
                combo.setDisable(!enabled);
                List<StorageCellChoice> cells =
                        enabled ? cellsLoader.apply(warehouse.id()) : List.of();
                syncing = true;
                try {
                    combo.setItems(FXCollections.observableArrayList(cells));
                    StorageCellChoice current = row.cell();
                    StorageCellChoice valid = current;
                    if (current != null) {
                        UUID cellId = current.id();
                        boolean belongs =
                                warehouse != null
                                        && Objects.equals(current.warehouseId(), warehouse.id())
                                        && cells.stream().anyMatch(c -> c.id().equals(cellId));
                        if (!belongs) {
                            row.setCell(null);
                            valid = null;
                        }
                    }
                    if (!Objects.equals(combo.getValue(), valid)) {
                        combo.setValue(valid);
                    }
                } finally {
                    syncing = false;
                }
            }

            private void detachWarehouseListener() {
                if (bound != null) {
                    bound.warehouseProperty().removeListener(warehouseListener);
                    bound = null;
                }
            }

            private ReceiptDialogRow boundRow() {
                return getTableRow() == null ? null : getTableRow().getItem();
            }
        };
    }

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP",
            justification = "JavaFX Dialog must be returned for showAndWait by the caller")
    public static final class ReceiptDialogSession {
        private final Dialog<ButtonType> dialog;
        private final ButtonType submitType;
        private final ObservableList<ReceiptDialogRow> rows;
        private final TableView<ReceiptDialogRow> table;
        private final Label errorLabel;

        ReceiptDialogSession(
                Dialog<ButtonType> dialog,
                ButtonType submitType,
                ObservableList<ReceiptDialogRow> rows,
                TableView<ReceiptDialogRow> table,
                Label errorLabel) {
            this.dialog = dialog;
            this.submitType = submitType;
            this.rows = rows;
            this.table = table;
            this.errorLabel = errorLabel;
        }

        public Dialog<ButtonType> dialog() {
            return dialog;
        }

        public ButtonType submitType() {
            return submitType;
        }

        TableView<ReceiptDialogRow> table() {
            return table;
        }

        ObservableList<ReceiptDialogRow> rows() {
            return rows;
        }

        Label errorLabel() {
            return errorLabel;
        }

        void showError(String message) {
            errorLabel.setText(message == null ? "" : message);
            boolean visible = message != null && !message.isBlank();
            errorLabel.setVisible(visible);
            errorLabel.setManaged(visible);
        }

        void clearError() {
            showError("");
        }

        public boolean isErrorVisible() {
            return errorLabel.isVisible();
        }

        public String errorText() {
            return errorLabel.getText() == null ? "" : errorLabel.getText();
        }

        public List<ReceiptLineSubmission> requireSubmission() {
            List<ReceiptLineSubmission> lines = new java.util.ArrayList<>();
            for (ReceiptDialogRow row : rows) {
                String article = blankToNull(row.materialTextProperty().get());
                if (article == null) {
                    throw new IllegalArgumentException("Укажите материал.");
                }
                String name = blankToNull(row.nameTextProperty().get());
                if (name == null) {
                    throw new IllegalArgumentException("Укажите наименование.");
                }
                BigDecimal qty =
                        DecimalQuantityParser.parsePositive(
                                row.quantityTextProperty().get(), "количество");
                String unit = blankToNull(row.unitOfMeasure());
                if (unit == null) {
                    throw new IllegalArgumentException("Укажите единицу измерения.");
                }
                WarehouseChoice warehouse = row.warehouse();
                StorageCellChoice cell = row.cell();
                if (warehouse == null) {
                    throw new IllegalArgumentException("Укажите склад.");
                }
                if (cell == null) {
                    throw new IllegalArgumentException("Укажите ячейку.");
                }
                lines.add(
                        new ReceiptLineSubmission(
                                article,
                                name,
                                nullToEmpty(row.colorTextProperty().get()),
                                nullToEmpty(row.sizeTextProperty().get()),
                                unit,
                                qty,
                                warehouse.id(),
                                cell.id()));
            }
            if (lines.isEmpty()) {
                throw new IllegalArgumentException("Добавьте хотя бы одну строку поступления.");
            }
            return List.copyOf(lines);
        }
    }

    public record ReceiptLineSubmission(
            String article,
            String name,
            String color,
            String size,
            String unitOfMeasure,
            BigDecimal quantity,
            UUID warehouseId,
            UUID storageCellId) {
        public ReceiptLineSubmission {
            Objects.requireNonNull(article, "article");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
            Objects.requireNonNull(quantity, "quantity");
            Objects.requireNonNull(warehouseId, "warehouseId");
            Objects.requireNonNull(storageCellId, "storageCellId");
        }
    }

    static final class ReceiptDialogRow {
        private final StringProperty materialText = new SimpleStringProperty("");
        private final StringProperty nameText = new SimpleStringProperty("");
        private final StringProperty colorText = new SimpleStringProperty("");
        private final StringProperty sizeText = new SimpleStringProperty("");
        private final StringProperty quantityText =
                new SimpleStringProperty(DecimalUiFormat.formatRu(BigDecimal.ONE));
        private String unitOfMeasure;
        private final ObjectProperty<WarehouseChoice> warehouse = new SimpleObjectProperty<>();
        private final ObjectProperty<StorageCellChoice> cell = new SimpleObjectProperty<>();

        StringProperty materialTextProperty() {
            return materialText;
        }

        StringProperty nameTextProperty() {
            return nameText;
        }

        StringProperty colorTextProperty() {
            return colorText;
        }

        StringProperty sizeTextProperty() {
            return sizeText;
        }

        StringProperty quantityTextProperty() {
            return quantityText;
        }

        String unitOfMeasure() {
            return unitOfMeasure;
        }

        void setUnitOfMeasure(String unitOfMeasure) {
            this.unitOfMeasure = unitOfMeasure;
        }

        ObjectProperty<WarehouseChoice> warehouseProperty() {
            return warehouse;
        }

        WarehouseChoice warehouse() {
            return warehouse.get();
        }

        void setWarehouse(WarehouseChoice warehouse) {
            this.warehouse.set(warehouse);
        }

        /**
         * Applies warehouse selection and clears cell when warehouse changes so the cell column can
         * reload without a TableView.refresh().
         */
        void applyWarehouseSelection(WarehouseChoice selected) {
            WarehouseChoice previous = warehouse.get();
            warehouse.set(selected);
            if (!Objects.equals(previous, selected)) {
                cell.set(null);
            }
        }

        ObjectProperty<StorageCellChoice> cellProperty() {
            return cell;
        }

        StorageCellChoice cell() {
            return cell.get();
        }

        void setCell(StorageCellChoice cell) {
            this.cell.set(cell);
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}

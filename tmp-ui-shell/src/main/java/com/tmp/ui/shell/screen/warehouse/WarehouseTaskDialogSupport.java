package com.tmp.ui.shell.screen.warehouse;

import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.ActionEditRow;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.TaskRow;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskKind;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskState;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.beans.property.SimpleStringProperty;

/**
 * Compact warehouse task dialog: header, material lines, contextual actions. Does not create
 * warehouse topology (no «Добавить ячейку»).
 */
public final class WarehouseTaskDialogSupport {

    public static final String TITLE = "Задача склада";
    public static final String CLOSE_BUTTON = "Закрыть";
    public static final String TAKE_BUTTON = "Взять в работу";
    public static final String SEND_BUTTON = "Передать";
    public static final String RECEIVE_BUTTON = "Принять";
    public static final String REJECT_BUTTON = "Отклонить";
    public static final String RETURN_BUTTON = "Вернуть материалы";

    public static final List<String> COLUMN_HEADERS =
            List.of(
                    "Артикул",
                    "Наименование",
                    "Цвет",
                    "Размер",
                    "Требуется",
                    "Ед.",
                    "Ячейка",
                    "Количество");

    private WarehouseTaskDialogSupport() {}

    public static String dialogTitleFor(WarehouseTaskKind kind) {
        return switch (kind) {
            case TRANSFER_PREPARATION -> "Подготовка материалов";
            case TRANSFER_RECEIPT -> "Приёмка материалов";
            case RETURN_MATERIALS -> "Возврат материалов";
        };
    }

    public static String referenceQuantityHeader(WarehouseTaskKind kind) {
        return switch (kind) {
            case TRANSFER_PREPARATION -> "Требуется";
            case TRANSFER_RECEIPT -> "Отправлено";
            case RETURN_MATERIALS -> "К возврату";
        };
    }

    /**
     * Allows empty, digits, and at most one decimal separator ({@code .} or {@code ,}). Rejects
     * letters, signs, and multiple separators so TextField caret is not reset by invalid rewrites.
     */
    public static boolean isAllowedQuantityInput(String text) {
        if (text == null || text.isEmpty()) {
            return true;
        }
        int separators = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch >= '0' && ch <= '9') {
                continue;
            }
            if (ch == '.' || ch == ',') {
                separators++;
                if (separators > 1) {
                    return false;
                }
                continue;
            }
            return false;
        }
        return true;
    }

    public static TextFormatter<String> quantityTextFormatter() {
        UnaryOperator<TextFormatter.Change> filter =
                change -> {
                    String newText = change.getControlNewText();
                    return isAllowedQuantityInput(newText) ? change : null;
                };
        return new TextFormatter<>(filter);
    }

    public static TaskDialogSession createTaskDialog(
            TaskRow task,
            ObservableList<ActionEditRow> actionLines,
            ObservableList<StorageCellChoice> cellChoices,
            BooleanProperty canTake,
            BooleanProperty editorsEnabled,
            BooleanProperty canSend,
            BooleanProperty canReceive,
            BooleanProperty canReject,
            BooleanProperty canReturn,
            BooleanProperty detailLoading,
            Runnable onTake,
            Runnable onSend,
            Runnable onReceive,
            Consumer<String> onReject,
            Runnable onReturn) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(actionLines, "actionLines");
        Objects.requireNonNull(cellChoices, "cellChoices");
        Objects.requireNonNull(editorsEnabled, "editorsEnabled");

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(TITLE);
        dialog.setResizable(true);
        ButtonType closeType = new ButtonType(CLOSE_BUTTON, ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().add(closeType);
        dialog.getDialogPane().getStyleClass().add("tmp-dialog");
        dialog.getDialogPane().setPrefSize(1020, 520);
        dialog.getDialogPane().setMinSize(860, 400);

        Label titleLabel = new Label(dialogTitleFor(task.taskKind()));
        titleLabel.getStyleClass().add("tmp-screen-title");

        Label routeLabel = new Label(task.routeLabel());
        routeLabel.getStyleClass().add("tmp-text-muted");
        routeLabel.setWrapText(true);

        Label orderLabel = new Label();
        updateOrderLabel(orderLabel, task);

        Label loadingLabel = new Label("Загрузка...");
        loadingLabel.getStyleClass().add("tmp-text-muted");
        loadingLabel.visibleProperty().bind(detailLoading);
        loadingLabel.managedProperty().bind(detailLoading);

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("tmp-message-error");
        errorLabel.setWrapText(true);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        TableView<ActionEditRow> table = new TableView<>(actionLines);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(280);
        table.setPlaceholder(new Label("Нет строк материалов"));
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<ActionEditRow, String> articleCol = new TableColumn<>(COLUMN_HEADERS.get(0));
        articleCol.setCellValueFactory(
                c -> new SimpleStringProperty(nullToEmpty(c.getValue().article())));
        TableColumn<ActionEditRow, String> nameCol = new TableColumn<>(COLUMN_HEADERS.get(1));
        nameCol.setCellValueFactory(
                c -> new SimpleStringProperty(nullToEmpty(c.getValue().name())));
        TableColumn<ActionEditRow, String> colorCol = new TableColumn<>(COLUMN_HEADERS.get(2));
        colorCol.setCellValueFactory(
                c -> new SimpleStringProperty(nullToEmpty(c.getValue().color())));
        TableColumn<ActionEditRow, String> sizeCol = new TableColumn<>(COLUMN_HEADERS.get(3));
        sizeCol.setCellValueFactory(
                c -> new SimpleStringProperty(nullToEmpty(c.getValue().size())));
        TableColumn<ActionEditRow, String> refQtyCol =
                new TableColumn<>(referenceQuantityHeader(task.taskKind()));
        refQtyCol.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().referenceQuantityText()));
        TableColumn<ActionEditRow, String> unitCol = new TableColumn<>(COLUMN_HEADERS.get(5));
        unitCol.setCellValueFactory(
                c -> new SimpleStringProperty(nullToEmpty(c.getValue().unitOfMeasure())));
        TableColumn<ActionEditRow, StorageCellChoice> cellCol =
                new TableColumn<>(COLUMN_HEADERS.get(6));
        cellCol.setCellValueFactory(c -> c.getValue().storageCellProperty());
        cellCol.setCellFactory(col -> cellComboCell(cellChoices, editorsEnabled));
        TableColumn<ActionEditRow, String> qtyCol = new TableColumn<>(COLUMN_HEADERS.get(7));
        qtyCol.setCellValueFactory(c -> c.getValue().quantityTextProperty());
        qtyCol.setCellFactory(col -> quantityCell(editorsEnabled));

        table.getColumns()
                .setAll(
                        articleCol,
                        nameCol,
                        colorCol,
                        sizeCol,
                        refQtyCol,
                        unitCol,
                        cellCol,
                        qtyCol);

        Button takeButton = new Button(TAKE_BUTTON);
        takeButton.getStyleClass().add("tmp-button-secondary");
        takeButton.disableProperty().bind(canTake.not());
        takeButton.visibleProperty().bind(canTake);
        takeButton.managedProperty().bind(canTake);
        takeButton.setOnAction(e -> onTake.run());

        Button sendButton = new Button(SEND_BUTTON);
        sendButton.getStyleClass().add("tmp-button-primary");
        sendButton.disableProperty().bind(canSend.not());
        sendButton.visibleProperty().bind(canSend);
        sendButton.managedProperty().bind(canSend);
        sendButton.setOnAction(e -> onSend.run());

        Button receiveButton = new Button(RECEIVE_BUTTON);
        receiveButton.getStyleClass().add("tmp-button-primary");
        receiveButton.disableProperty().bind(canReceive.not());
        receiveButton.visibleProperty().bind(canReceive);
        receiveButton.managedProperty().bind(canReceive);
        receiveButton.setOnAction(e -> onReceive.run());

        Button rejectButton = new Button(REJECT_BUTTON);
        rejectButton.getStyleClass().add("tmp-button-danger");
        rejectButton.disableProperty().bind(canReject.not());
        rejectButton.visibleProperty().bind(canReject);
        rejectButton.managedProperty().bind(canReject);
        rejectButton.setOnAction(
                e -> {
                    Optional<String> reason = promptRejectReason(dialog);
                    reason.ifPresent(onReject);
                });

        Button returnButton = new Button(RETURN_BUTTON);
        returnButton.getStyleClass().add("tmp-button-primary");
        returnButton.disableProperty().bind(canReturn.not());
        returnButton.visibleProperty().bind(canReturn);
        returnButton.managedProperty().bind(canReturn);
        returnButton.setOnAction(e -> onReturn.run());

        HBox actions =
                new HBox(8, takeButton, sendButton, receiveButton, rejectButton, returnButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox root =
                new VBox(
                        10,
                        titleLabel,
                        routeLabel,
                        orderLabel,
                        loadingLabel,
                        table,
                        actions,
                        errorLabel);
        root.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(root);

        dialog.setOnShown(
                e -> {
                    Button close = (Button) dialog.getDialogPane().lookupButton(closeType);
                    if (close != null) {
                        close.getStyleClass().add("tmp-button-secondary");
                    }
                });

        return new TaskDialogSession(
                dialog, closeType, titleLabel, routeLabel, orderLabel, refQtyCol, errorLabel, table);
    }

    public static void refreshHeader(TaskDialogSession session, TaskRow task) {
        if (session == null || task == null) {
            return;
        }
        session.titleLabel().setText(dialogTitleFor(task.taskKind()));
        session.routeLabel().setText(task.routeLabel());
        updateOrderLabel(session.orderLabel(), task);
        session.referenceQuantityColumn().setText(referenceQuantityHeader(task.taskKind()));
    }

    private static void updateOrderLabel(Label orderLabel, TaskRow task) {
        String order = task.orderNumberText();
        if (order == null || order.isBlank() || "—".equals(order)) {
            orderLabel.setText("");
            orderLabel.setManaged(false);
            orderLabel.setVisible(false);
        } else {
            orderLabel.setText("Заказ: " + order);
            orderLabel.setManaged(true);
            orderLabel.setVisible(true);
        }
    }

    private static Optional<String> promptRejectReason(Dialog<?> owner) {
        Dialog<String> dialog = new Dialog<>();
        dialog.initOwner(owner.getDialogPane().getScene() == null
                ? null
                : owner.getDialogPane().getScene().getWindow());
        dialog.setTitle("Отклонение перемещения");
        dialog.setHeaderText("Укажите причину отклонения");
        ButtonType rejectType = new ButtonType(REJECT_BUTTON, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(rejectType, cancelType);
        dialog.getDialogPane().getStyleClass().add("tmp-dialog");
        TextArea reasonArea = new TextArea();
        reasonArea.setPromptText("Причина");
        reasonArea.setWrapText(true);
        reasonArea.setPrefRowCount(4);
        dialog.getDialogPane().setContent(reasonArea);
        Button rejectButton = (Button) dialog.getDialogPane().lookupButton(rejectType);
        rejectButton.getStyleClass().add("tmp-button-danger");
        Button cancelButton = (Button) dialog.getDialogPane().lookupButton(cancelType);
        cancelButton.getStyleClass().add("tmp-button-secondary");
        rejectButton
                .disableProperty()
                .bind(
                        Bindings.createBooleanBinding(
                                () -> reasonArea.getText() == null || reasonArea.getText().isBlank(),
                                reasonArea.textProperty()));
        dialog.setResultConverter(button -> button == rejectType ? reasonArea.getText() : null);
        dialog.setOnShown(e -> reasonArea.requestFocus());
        return dialog.showAndWait();
    }

    private static TableCell<ActionEditRow, StorageCellChoice> cellComboCell(
            ObservableList<StorageCellChoice> choices, BooleanProperty editorsEnabled) {
        return new TableCell<>() {
            private final ComboBox<StorageCellChoice> combo = new ComboBox<>();

            {
                combo.setItems(choices);
                combo.disableProperty().bind(editorsEnabled.not());
                combo.valueProperty()
                        .addListener(
                                (obs, oldValue, newValue) -> {
                                    if (!editorsEnabled.get()) {
                                        return;
                                    }
                                    ActionEditRow row =
                                            getTableRow() == null ? null : getTableRow().getItem();
                                    if (row != null && !isEmpty()) {
                                        row.storageCellProperty().set(newValue);
                                    }
                                });
            }

            @Override
            protected void updateItem(StorageCellChoice item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                if (!Objects.equals(combo.getValue(), item)) {
                    combo.setValue(item);
                }
                setGraphic(combo);
            }
        };
    }

    private static TableCell<ActionEditRow, String> quantityCell(BooleanProperty editorsEnabled) {
        return new TableCell<>() {
            private final TextField field = new TextField();
            private boolean syncingFromModel;

            {
                field.setTextFormatter(quantityTextFormatter());
                field.textProperty()
                        .addListener(
                                (obs, oldValue, newValue) -> {
                                    if (syncingFromModel || !editorsEnabled.get()) {
                                        return;
                                    }
                                    ActionEditRow row =
                                            getTableRow() == null ? null : getTableRow().getItem();
                                    if (row != null && row.quantityEditable() && !isEmpty()) {
                                        if (!Objects.equals(
                                                row.quantityTextProperty().get(), newValue)) {
                                            row.quantityTextProperty().set(newValue);
                                        }
                                    }
                                });
                editorsEnabled.addListener((obs, o, n) -> updateItem(getItem(), isEmpty()));
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                ActionEditRow row = getTableRow().getItem();
                boolean editable = editorsEnabled.get() && row.quantityEditable();
                String display = item == null ? "" : item;
                if (editable) {
                    if (!field.isFocused() && !Objects.equals(field.getText(), display)) {
                        syncingFromModel = true;
                        try {
                            field.setText(display);
                        } finally {
                            syncingFromModel = false;
                        }
                    }
                    setGraphic(field);
                    setText(null);
                } else {
                    setGraphic(null);
                    setText(display);
                }
            }
        };
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** Whether the task row still has operational actions (not a finished/closed inbox item). */
    public static boolean hasOperationalActions(TaskRow task) {
        return task != null
                && (task.taskState() == WarehouseTaskState.NEW
                        || task.taskState() == WarehouseTaskState.IN_WORK);
    }

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP",
            justification = "Dialog session exposes JavaFX nodes for controller refresh")
    public static final class TaskDialogSession {
        private final Dialog<ButtonType> dialog;
        private final ButtonType closeType;
        private final Label titleLabel;
        private final Label routeLabel;
        private final Label orderLabel;
        private final TableColumn<ActionEditRow, String> referenceQuantityColumn;
        private final Label errorLabel;
        private final TableView<ActionEditRow> table;

        TaskDialogSession(
                Dialog<ButtonType> dialog,
                ButtonType closeType,
                Label titleLabel,
                Label routeLabel,
                Label orderLabel,
                TableColumn<ActionEditRow, String> referenceQuantityColumn,
                Label errorLabel,
                TableView<ActionEditRow> table) {
            this.dialog = dialog;
            this.closeType = closeType;
            this.titleLabel = titleLabel;
            this.routeLabel = routeLabel;
            this.orderLabel = orderLabel;
            this.referenceQuantityColumn = referenceQuantityColumn;
            this.errorLabel = errorLabel;
            this.table = table;
        }

        public Dialog<ButtonType> dialog() {
            return dialog;
        }

        public ButtonType closeType() {
            return closeType;
        }

        public Label titleLabel() {
            return titleLabel;
        }

        public Label routeLabel() {
            return routeLabel;
        }

        public Label orderLabel() {
            return orderLabel;
        }

        public TableColumn<ActionEditRow, String> referenceQuantityColumn() {
            return referenceQuantityColumn;
        }

        public TableView<ActionEditRow> table() {
            return table;
        }

        public void showError(String message) {
            if (message == null || message.isBlank()) {
                clearError();
                return;
            }
            errorLabel.setText(message);
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
        }

        public void clearError() {
            errorLabel.setText("");
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
        }

        public void close() {
            dialog.setResult(closeType);
            dialog.close();
        }
    }
}
